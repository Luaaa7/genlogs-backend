package com.genlogs.app.vitrina;

import com.genlogs.app.enums.EstadoPublicacion;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfig;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfigRepository;
import com.genlogs.app.model.Producto;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.service.ClavesParametro;
import com.genlogs.app.service.NotificacionService;
import com.genlogs.app.service.ParametrosService;
import com.genlogs.app.sourcing.ConsultaFuente;
import com.genlogs.app.sourcing.ConsultaFuenteService;
import com.genlogs.app.sourcing.EstadoConsultaFuente;
import com.genlogs.app.sourcing.HistorialSourcing;
import com.genlogs.app.sourcing.HistorialSourcingRepository;
import com.genlogs.app.sourcing.ResultadoBusqueda;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RF-83 · Verificación de la vitrina. R1 (v3.3, BPMN 10.1): busca con el 9-B.1 sin guardar,
 * solo en la tienda activa del historial y con un cupo por tienda de
 * min(VERIFICACION_MAX_DIA, limite_diario / 5).
 */
class VerificacionVitrinaServiceTest {

    private static final LocalDateTime HOY = LocalDateTime.of(2026, 11, 11, 6, 10);

    private ProductoRepository productoRepo;
    private HistorialSourcingRepository historialRepo;
    private NotificacionService notificaciones;
    private FuenteBusquedaConfigRepository fuenteRepo;
    private ConsultaFuenteService consulta;
    private ParametrosService parametros;
    private final List<FuenteBusquedaConfig> activas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        productoRepo = mock(ProductoRepository.class);
        historialRepo = mock(HistorialSourcingRepository.class);
        notificaciones = mock(NotificacionService.class);
        fuenteRepo = mock(FuenteBusquedaConfigRepository.class);
        consulta = mock(ConsultaFuenteService.class);
        parametros = mock(ParametrosService.class);
        when(parametros.getEntero(ClavesParametro.VERIFICACION_DIAS)).thenReturn(15);
        when(parametros.getEntero(ClavesParametro.OCULTAR_SIN_VERIF_DIAS)).thenReturn(60);
        when(parametros.getEntero(ClavesParametro.OCULTAR_FALLAS)).thenReturn(2);
        when(parametros.buscar(ClavesParametro.VERIFICACION_MAX_DIA)).thenReturn(Optional.of("10"));
        when(fuenteRepo.findByActivaTrueAndStatusOrderByIdFuente("A")).thenReturn(activas);
        when(productoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepo.findFirstByIdProductoOrderByCreadoEnDesc(anyLong())).thenReturn(Optional.empty());
    }

    private VerificacionVitrinaService servicio() {
        VerificacionVitrinaService s = new VerificacionVitrinaService(productoRepo, historialRepo, parametros,
                notificaciones, fuenteRepo, consulta);
        s.setClock(Clock.fixed(Instant.parse("2026-11-11T11:10:00Z"), ZoneId.of("America/Lima")));
        return s;
    }

    private FuenteBusquedaConfig activa(CodigoFuente codigo, int limiteDiario) {
        FuenteBusquedaConfig f = new FuenteBusquedaConfig();
        f.setCodigo(codigo);
        f.setNombre(codigo.name());
        f.setActiva(true);
        f.setLimiteDiario(limiteDiario);
        activas.add(f);
        return f;
    }

    private Producto publicado(long id, LocalDateTime verificado, int fallas) {
        return Producto.builder().idProducto(id).codigoProducto("GLB-" + id).nombreProducto("Repuesto " + id)
                .numeroParte("NP-" + id).estadoPublicacion(EstadoPublicacion.PUBLICADO).visibleWeb(true)
                .fechaUltimaVerificacion(verificado).fallasCotizacion((short) fallas)
                .precioReferencial(BigDecimal.TEN).build();
    }

    @SuppressWarnings("unchecked")
    private void hayPublicados(Producto... productos) {
        when(productoRepo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(productos));
    }

    private void historial(long idProducto, String fuente) {
        when(historialRepo.findFirstByIdProductoOrderByCreadoEnDesc(idProducto))
                .thenReturn(Optional.of(HistorialSourcing.builder().fuente(fuente).build()));
    }

    private static ConsultaFuente resultado(EstadoConsultaFuente estado, String numeroParte) {
        List<ResultadoBusqueda> fichas = estado == EstadoConsultaFuente.OK
                ? List.of(new ResultadoBusqueda("x", null, numeroParte, BigDecimal.ONE, "USD", "https://e/1", null, null))
                : List.of();
        return ConsultaFuente.de("EBAY", estado, null, fichas);
    }

    private void responde(String numeroParte, EstadoConsultaFuente estado) {
        when(consulta.consultar(any(), argThat(c -> c != null && numeroParte.equals(c.numeroParte()))))
                .thenReturn(resultado(estado, numeroParte));
    }

    @Test
    void ocultaPorFallasYPorSesentaDiasSinVerificar_yAvisaUnaVez() {
        Producto conFallas = publicado(1, HOY.minusDays(1), 2);
        Producto viejo = publicado(2, HOY.minusDays(61), 0);
        Producto alDia = publicado(3, HOY.minusDays(3), 1);
        hayPublicados(conFallas, viejo, alDia);

        VerificacionVitrinaService.Resumen r = servicio().verificarPublicados();

        assertThat(r).isEqualTo(new VerificacionVitrinaService.Resumen(3, 0, 2));
        assertThat(conFallas.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.OCULTO);
        assertThat(viejo.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.OCULTO);
        assertThat(viejo.getVisibleWeb()).isFalse();
        assertThat(alDia.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.PUBLICADO);
        verify(notificaciones, times(1)).avisarRol(eq(RolSistema.ASISTENTE_LOGISTICO), eq(TipoAviso.GENERAL),
                anyString(), contains("2 repuestos"), anyString(), eq(false));
        verifyNoInteractions(consulta);
    }

    @Test
    void r1_encontradoSeVerifica_noEncontradoSeOculta() {
        activa(CodigoFuente.EBAY, 50);
        Producto sigue = publicado(1, HOY.minusDays(16), 0);
        Producto yaNo = publicado(2, HOY.minusDays(16), 0);
        hayPublicados(sigue, yaNo);
        historial(1, "EBAY");
        historial(2, "EBAY");
        responde("NP-1", EstadoConsultaFuente.OK);
        responde("NP-2", EstadoConsultaFuente.SIN_RESULTADOS);

        VerificacionVitrinaService.Resumen r = servicio().verificarPublicados();

        assertThat(r.verificados()).isEqualTo(1);
        assertThat(r.ocultados()).isEqualTo(1);
        assertThat(sigue.getFechaUltimaVerificacion()).isEqualTo(HOY);
        assertThat(yaNo.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.OCULTO);
    }

    @Test
    void r1_fuenteInactivaNoSeConsultaNiOculta() {
        // EBAY no está entre las activas.
        Producto p = publicado(1, HOY.minusDays(20), 0);
        hayPublicados(p);
        historial(1, "EBAY");

        VerificacionVitrinaService.Resumen r = servicio().verificarPublicados();

        assertThat(r.ocultados()).isZero();
        assertThat(p.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.PUBLICADO);
        verifyNoInteractions(consulta);
        verifyNoInteractions(notificaciones);
    }

    @Test
    void r1_fuenteCaidaBloqueadaOQuePideVerificacionNoOculta() {
        activa(CodigoFuente.EBAY, 50);
        Producto caida = publicado(1, HOY.minusDays(20), 0);
        Producto bloqueada = publicado(2, HOY.minusDays(20), 0);
        Producto pideCodigo = publicado(3, HOY.minusDays(20), 0);
        Producto lanza = publicado(4, HOY.minusDays(20), 0);
        hayPublicados(caida, bloqueada, pideCodigo, lanza);
        for (long id = 1; id <= 4; id++) historial(id, "EBAY");
        responde("NP-1", EstadoConsultaFuente.CON_ERROR);
        responde("NP-2", EstadoConsultaFuente.BLOQUEADA);
        responde("NP-3", EstadoConsultaFuente.REQUIERE_VERIFICACION);
        when(consulta.consultar(any(), argThat(c -> c != null && "NP-4".equals(c.numeroParte()))))
                .thenThrow(new IllegalStateException("servicio de lectura caído"));

        VerificacionVitrinaService.Resumen r = servicio().verificarPublicados();

        assertThat(r.ocultados()).isZero();
        assertThat(List.of(caida, bloqueada, pideCodigo, lanza))
                .allMatch(p -> p.getEstadoPublicacion() == EstadoPublicacion.PUBLICADO);
        verifyNoInteractions(notificaciones);
    }

    @Test
    void r1_capturadoPorUrlOProveedorNoSeConsulta() {
        activa(CodigoFuente.EBAY, 50);
        Producto proveedor = publicado(1, HOY.minusDays(20), 0);
        Producto sinHistorial = publicado(2, HOY.minusDays(20), 0);
        hayPublicados(proveedor, sinHistorial);
        historial(1, "PROVEEDOR");

        VerificacionVitrinaService.Resumen r = servicio().verificarPublicados();

        assertThat(r.ocultados()).isZero();
        verifyNoInteractions(consulta);
    }

    @Test
    void r1_cupoPorTienda_conLimite20SoloConsulta4_yElRestoQuedaParaOtroDia() {
        activa(CodigoFuente.EBAY, 20);              // min(10, 20 / 5) = 4
        Producto[] productos = new Producto[6];
        for (int i = 0; i < 6; i++) {
            productos[i] = publicado(i + 1, HOY.minusDays(16), 0);
            historial(i + 1, "EBAY");
        }
        hayPublicados(productos);
        when(consulta.consultar(any(), any())).thenReturn(resultado(EstadoConsultaFuente.SIN_RESULTADOS, "x"));

        VerificacionVitrinaService.Resumen r = servicio().verificarPublicados();

        verify(consulta, times(4)).consultar(any(), any());
        assertThat(r.ocultados()).isEqualTo(4);
        assertThat(productos[4].getEstadoPublicacion()).isEqualTo(EstadoPublicacion.PUBLICADO);
        assertThat(productos[5].getEstadoPublicacion()).isEqualTo(EstadoPublicacion.PUBLICADO);
    }

    @Test
    void r1_cupoPorTienda_usaVerificacionMaxDiaYCuentaAunqueLaTiendaFalle() {
        when(parametros.buscar(ClavesParametro.VERIFICACION_MAX_DIA)).thenReturn(Optional.of("2"));
        activa(CodigoFuente.EBAY, 50);              // min(2, 50 / 5) = 2
        Producto[] productos = new Producto[3];
        for (int i = 0; i < 3; i++) {
            productos[i] = publicado(i + 1, HOY.minusDays(16), 0);
            historial(i + 1, "EBAY");
        }
        hayPublicados(productos);
        when(consulta.consultar(any(), any())).thenReturn(resultado(EstadoConsultaFuente.BLOQUEADA, "x"));

        servicio().verificarPublicados();

        verify(consulta, times(2)).consultar(any(), any());
    }

    @Test
    void r1_loQueNoLlegaALaTiendaNoGastaCupo() {
        when(parametros.buscar(ClavesParametro.VERIFICACION_MAX_DIA)).thenReturn(Optional.of("1"));
        activa(CodigoFuente.EBAY, 50);              // cupo 1
        Producto sinSesion = publicado(1, HOY.minusDays(16), 0);
        Producto siguiente = publicado(2, HOY.minusDays(16), 0);
        hayPublicados(sinSesion, siguiente);
        historial(1, "EBAY");
        historial(2, "EBAY");
        responde("NP-1", EstadoConsultaFuente.OMITIDA_SESION);
        responde("NP-2", EstadoConsultaFuente.OK);

        VerificacionVitrinaService.Resumen r = servicio().verificarPublicados();

        verify(consulta, times(2)).consultar(any(), any());
        assertThat(r.verificados()).isEqualTo(1);
    }

    @Test
    void r1_cupoPorCadaTiendaPorSeparado() {
        when(parametros.buscar(ClavesParametro.VERIFICACION_MAX_DIA)).thenReturn(Optional.of("1"));
        FuenteBusquedaConfig ebay = activa(CodigoFuente.EBAY, 50);
        FuenteBusquedaConfig ml = activa(CodigoFuente.MERCADO_LIBRE, 50);
        Producto e1 = publicado(1, HOY.minusDays(16), 0);
        Producto e2 = publicado(2, HOY.minusDays(16), 0);
        Producto m1 = publicado(3, HOY.minusDays(16), 0);
        hayPublicados(e1, e2, m1);
        historial(1, "EBAY");
        historial(2, "EBAY");
        historial(3, "MERCADO_LIBRE");
        when(consulta.consultar(any(), any())).thenReturn(resultado(EstadoConsultaFuente.OK, "x"));

        servicio().verificarPublicados();

        verify(consulta, times(1)).consultar(eq(ebay), any());
        verify(consulta, times(1)).consultar(eq(ml), any());
    }

    @Test
    void r1_cupoEsElMenorEntreElParametroYLaQuintaParteDelLimite() {
        FuenteBusquedaConfig f = new FuenteBusquedaConfig();
        f.setLimiteDiario(50);
        assertThat(VerificacionVitrinaService.cupo(f, 10)).isEqualTo(10);
        f.setLimiteDiario(80);
        assertThat(VerificacionVitrinaService.cupo(f, 10)).isEqualTo(10);
        f.setLimiteDiario(20);
        assertThat(VerificacionVitrinaService.cupo(f, 10)).isEqualTo(4);
        f.setLimiteDiario(3);
        assertThat(VerificacionVitrinaService.cupo(f, 10)).isZero();
    }

    @Test
    void r1_sinLaFilaVerificacionMaxDiaUsa10() {
        when(parametros.buscar(ClavesParametro.VERIFICACION_MAX_DIA)).thenReturn(Optional.empty());
        activa(CodigoFuente.EBAY, 1000);            // min(10, 200) = 10
        Producto[] productos = new Producto[12];
        for (int i = 0; i < 12; i++) {
            productos[i] = publicado(i + 1, HOY.minusDays(16), 0);
            historial(i + 1, "EBAY");
        }
        hayPublicados(productos);
        when(consulta.consultar(any(), any())).thenReturn(resultado(EstadoConsultaFuente.OK, "x"));

        servicio().verificarPublicados();

        verify(consulta, times(10)).consultar(any(), any());
    }

    @Test
    void r1_aliexpressSeGuardaComoAlibabaPeroSeConsultaComoAliexpress() {
        HistorialSourcing ali = HistorialSourcing.builder().fuente("ALIBABA").consulta("ALIEXPRESS | 1R-0750").build();
        HistorialSourcing alibaba = HistorialSourcing.builder().fuente("ALIBABA").consulta("1R-0750").build();
        assertThat(VerificacionVitrinaService.codigoDe(ali)).isEqualTo(CodigoFuente.ALIEXPRESS);
        assertThat(VerificacionVitrinaService.codigoDe(alibaba)).isEqualTo(CodigoFuente.ALIBABA);
        assertThat(VerificacionVitrinaService.codigoDe(HistorialSourcing.builder().fuente("PROVEEDOR").build())).isNull();
    }

    @Test
    void laSegundaFallaDeCotizacionLoOculta() {
        Producto p = publicado(5, HOY.minusDays(1), 1);
        when(productoRepo.findById(5L)).thenReturn(Optional.of(p));

        servicio().registrarFallaCotizacion(5L);

        assertThat(p.getFallasCotizacion()).isEqualTo((short) 2);
        assertThat(p.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.OCULTO);
    }

    @Test
    void verificarAManoActualizaLaFecha() {
        Producto p = publicado(6, HOY.minusDays(40), 0);
        when(productoRepo.findById(6L)).thenReturn(Optional.of(p));

        servicio().marcarVerificado(6L);

        assertThat(p.getFechaUltimaVerificacion()).isEqualTo(HOY);
    }
}
