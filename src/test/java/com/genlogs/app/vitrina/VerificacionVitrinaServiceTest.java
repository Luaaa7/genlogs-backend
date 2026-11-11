package com.genlogs.app.vitrina;

import com.genlogs.app.enums.EstadoPublicacion;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.model.Producto;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.service.ClavesParametro;
import com.genlogs.app.service.NotificacionService;
import com.genlogs.app.service.ParametrosService;
import com.genlogs.app.sourcing.FuenteBusqueda;
import com.genlogs.app.sourcing.HistorialSourcing;
import com.genlogs.app.sourcing.HistorialSourcingRepository;
import com.genlogs.app.sourcing.ResultadoBusqueda;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificacionVitrinaServiceTest {

    private static final LocalDateTime HOY = LocalDateTime.of(2026, 11, 11, 6, 10);

    private ProductoRepository productoRepo;
    private HistorialSourcingRepository historialRepo;
    private NotificacionService notificaciones;
    private FuenteBusqueda ebay;

    @BeforeEach
    void setUp() {
        productoRepo = mock(ProductoRepository.class);
        historialRepo = mock(HistorialSourcingRepository.class);
        notificaciones = mock(NotificacionService.class);
        ebay = mock(FuenteBusqueda.class);
        when(ebay.codigo()).thenReturn("EBAY");
        when(productoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepo.findFirstByIdProductoOrderByCreadoEnDesc(anyLong())).thenReturn(Optional.empty());
    }

    private VerificacionVitrinaService servicio(FuenteBusqueda... conectadas) {
        ParametrosService parametros = mock(ParametrosService.class);
        when(parametros.getEntero(ClavesParametro.VERIFICACION_DIAS)).thenReturn(15);
        when(parametros.getEntero(ClavesParametro.OCULTAR_SIN_VERIF_DIAS)).thenReturn(60);
        when(parametros.getEntero(ClavesParametro.OCULTAR_FALLAS)).thenReturn(2);
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        for (int i = 0; i < conectadas.length; i++) bf.registerSingleton("f" + i, conectadas[i]);
        VerificacionVitrinaService s = new VerificacionVitrinaService(productoRepo, historialRepo, parametros,
                notificaciones, bf.getBeanProvider(FuenteBusqueda.class));
        s.setClock(Clock.fixed(Instant.parse("2026-11-11T11:10:00Z"), ZoneId.of("America/Lima")));
        return s;
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
    }

    @Test
    void cadaQuinceDiasConsultaLaFuente_siSigueLoVerificaYSiNoLoOculta() {
        Producto sigue = publicado(1, HOY.minusDays(16), 0);
        Producto yaNo = publicado(2, HOY.minusDays(16), 0);
        hayPublicados(sigue, yaNo);
        when(historialRepo.findFirstByIdProductoOrderByCreadoEnDesc(anyLong()))
                .thenReturn(Optional.of(HistorialSourcing.builder().fuente("EBAY").build()));
        when(ebay.buscar(argThat(c -> c != null && "NP-1".equals(c.numeroParte()))))
                .thenReturn(List.of(new ResultadoBusqueda("x", null, "NP-1", BigDecimal.ONE, "USD", "https://e/1", null)));
        when(ebay.buscar(argThat(c -> c != null && "NP-2".equals(c.numeroParte())))).thenReturn(List.of());

        VerificacionVitrinaService.Resumen r = servicio(ebay).verificarPublicados();

        assertThat(r.verificados()).isEqualTo(1);
        assertThat(r.ocultados()).isEqualTo(1);
        assertThat(sigue.getFechaUltimaVerificacion()).isEqualTo(HOY);
        assertThat(yaNo.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.OCULTO);
    }

    @Test
    void capturadoPorUrlNoSeConsulta_ySiLaFuenteFallaNoSeOculta() {
        Producto porUrl = publicado(1, HOY.minusDays(20), 0);
        Producto fuenteCaida = publicado(2, HOY.minusDays(20), 0);
        hayPublicados(porUrl, fuenteCaida);
        when(historialRepo.findFirstByIdProductoOrderByCreadoEnDesc(1L))
                .thenReturn(Optional.of(HistorialSourcing.builder().fuente("AMAZON").build()));
        when(historialRepo.findFirstByIdProductoOrderByCreadoEnDesc(2L))
                .thenReturn(Optional.of(HistorialSourcing.builder().fuente("EBAY").build()));
        when(ebay.buscar(any())).thenThrow(new IllegalStateException("cuota agotada"));

        VerificacionVitrinaService.Resumen r = servicio(ebay).verificarPublicados();

        assertThat(r.ocultados()).isZero();
        assertThat(porUrl.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.PUBLICADO);
        assertThat(fuenteCaida.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.PUBLICADO);
        verifyNoInteractions(notificaciones);
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
