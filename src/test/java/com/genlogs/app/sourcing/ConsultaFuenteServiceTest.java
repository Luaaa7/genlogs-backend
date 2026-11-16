package com.genlogs.app.sourcing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.EstadoFuente;
import com.genlogs.app.fuentes.config.FichaFuente;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfig;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfigRepository;
import com.genlogs.app.fuentes.config.FuenteSelectorRepository;
import com.genlogs.app.fuentes.config.LectorFuente;
import com.genlogs.app.fuentes.config.LectorFuenteNoConectado;
import com.genlogs.app.fuentes.config.ResultadoLectura;
import com.genlogs.app.fuentes.config.SesionFuenteService;
import com.genlogs.app.repository.NotificacionRepository;
import com.genlogs.app.service.NotificacionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class ConsultaFuenteServiceTest {

    private FuenteBusquedaConfigRepository fuenteRepo;
    private FuenteSelectorRepository selectorRepo;
    private SesionFuenteService sesiones;
    private LectorFuente lector;
    private PausaFuente pausa;
    private NotificacionService notificaciones;
    private NotificacionRepository notificacionRepo;
    private ConsultaFuenteService servicio;
    private FuenteBusquedaConfig ebay;
    private ConsultaBusqueda consulta;

    @BeforeEach
    void setUp() {
        fuenteRepo = mock(FuenteBusquedaConfigRepository.class);
        selectorRepo = mock(FuenteSelectorRepository.class);
        sesiones = mock(SesionFuenteService.class);
        lector = mock(LectorFuente.class);
        pausa = mock(PausaFuente.class);
        notificaciones = mock(NotificacionService.class);
        notificacionRepo = mock(NotificacionRepository.class);
        when(selectorRepo.findByFuente_CodigoAndStatus(any(), eq("A"))).thenReturn(List.of());
        when(sesiones.sesionVigente(CodigoFuente.EBAY)).thenReturn(Optional.of("sesion-opaca"));
        when(notificacionRepo.existsByTipoAndEnlaceAndFechaCreacionGreaterThanEqualAndFechaCreacionLessThan(
                any(), any(), any(), any())).thenReturn(false);
        servicio = new ConsultaFuenteService(fuenteRepo, selectorRepo, sesiones, lector, pausa,
                notificaciones, notificacionRepo);
        ebay = fuente(CodigoFuente.EBAY, 50, 3);
        consulta = new ConsultaBusqueda(MetodoBusqueda.NUMERO_PARTE, "1R-0750", "Caterpillar",
                null, null, null);
    }

    private static FuenteBusquedaConfig fuente(CodigoFuente codigo, int limite, int pausaSeg) {
        FuenteBusquedaConfig f = new FuenteBusquedaConfig();
        f.setCodigo(codigo);
        f.setNombre(codigo.name());
        f.setUrlBase("https://ebay.example");
        f.setActiva(true);
        f.setEstado(EstadoFuente.ACTIVA);
        f.setLimiteDiario(limite);
        f.setPausaSegundos(pausaSeg);
        f.setUserCreate("PRUEBA");
        f.setProcessCreate("PRUEBA");
        return f;
    }

    private static FichaFuente ficha(String nombre, String precio, String enlace) {
        return new FichaFuente(nombre, "desc", precio, "USD", "https://img.example/1.jpg", enlace);
    }

    @Test
    void sinCuota_omiteYNoLee() {
        ebay.setLimiteDiario(1);
        when(lector.buscar(any(), any(), any(), any(), any())).thenReturn(ResultadoLectura.ok(List.of(
                ficha("Filtro", "18.50", "https://ebay.example/1"))));

        servicio.consultar(ebay, consulta);
        ConsultaFuente segunda = servicio.consultar(ebay, consulta);

        assertThat(segunda.estado()).isEqualTo(EstadoConsultaFuente.OMITIDA_CUOTA);
        assertThat(segunda.motivo()).containsIgnoringCase("cuota");
        verify(lector, times(1)).buscar(any(), any(), any(), any(), any());
        verify(pausa, times(1)).esperar(3);
    }

    @Test
    void respetaLaPausaConfigurada() {
        when(lector.buscar(any(), any(), any(), any(), any())).thenReturn(ResultadoLectura.sinResultados());
        servicio.consultar(ebay, consulta);
        verify(pausa).esperar(3);
        verify(sesiones).sesionVigente(CodigoFuente.EBAY);
    }

    @Test
    void sinSesion_omiteYNoLeeNiCambiaEstado() {
        when(sesiones.sesionVigente(CodigoFuente.EBAY)).thenReturn(Optional.empty());

        ConsultaFuente r = servicio.consultar(ebay, consulta);

        assertThat(r.estado()).isEqualTo(EstadoConsultaFuente.OMITIDA_SESION);
        verify(lector, never()).buscar(any(), any(), any(), any(), any());
        verify(sesiones, never()).marcarVerificacion(any(), any());
        verify(fuenteRepo, never()).save(any());
        assertThat(ebay.getEstado()).isEqualTo(EstadoFuente.ACTIVA);
    }

    @Test
    void pideLaSesionALuana() {
        when(lector.buscar(any(), any(), any(), any(), any())).thenReturn(ResultadoLectura.sinResultados());
        servicio.consultar(ebay, consulta);
        verify(sesiones).sesionVigente(CodigoFuente.EBAY);
        verify(lector).buscar(eq(CodigoFuente.EBAY), eq("https://ebay.example"), eq("sesion-opaca"),
                eq(consulta), any());
    }

    @Test
    void captcha_usaMarcarVerificacion() {
        when(lector.buscar(any(), any(), any(), any(), any()))
                .thenReturn(ResultadoLectura.verificacion("La tienda pidió un CAPTCHA al buscar."));

        ConsultaFuente r = servicio.consultar(ebay, consulta);

        assertThat(r.estado()).isEqualTo(EstadoConsultaFuente.REQUIERE_VERIFICACION);
        verify(sesiones).marcarVerificacion(CodigoFuente.EBAY, "La tienda pidió un CAPTCHA al buscar.");
        verify(notificaciones).avisarRol(any(), eq(TipoAviso.GENERAL), any(), any(),
                eq("/configuracion/fuentes/EBAY#lectura-verificacion"), eq(false));
    }

    @Test
    void bloqueo_noMarcaLaFuenteConError() {
        when(lector.buscar(any(), any(), any(), any(), any()))
                .thenReturn(ResultadoLectura.bloqueada("HTTP 403"));

        ConsultaFuente r = servicio.consultar(ebay, consulta);

        assertThat(r.estado()).isEqualTo(EstadoConsultaFuente.BLOQUEADA);
        assertThat(ebay.getEstado()).isEqualTo(EstadoFuente.ACTIVA);
        verify(fuenteRepo, never()).save(any());
        verify(sesiones, never()).marcarVerificacion(any(), any());
    }

    @Test
    void error_marcaFuenteYReintentaUnaVez() {
        when(lector.buscar(any(), any(), any(), any(), any()))
                .thenReturn(ResultadoLectura.error("tiempo agotado", true));

        ConsultaFuente r = servicio.consultar(ebay, consulta);

        assertThat(r.estado()).isEqualTo(EstadoConsultaFuente.CON_ERROR);
        assertThat(ebay.getEstado()).isEqualTo(EstadoFuente.CON_ERROR);
        verify(lector, times(2)).buscar(any(), any(), any(), any(), any());
        verify(fuenteRepo).save(ebay);
        verify(notificaciones).avisarRol(any(), eq(TipoAviso.GENERAL), any(), any(),
                eq("/configuracion/fuentes/EBAY#lectura-error"), eq(false));
    }

    @Test
    void avisoAlJefe_unaSolaVezAlDia() {
        when(notificacionRepo.existsByTipoAndEnlaceAndFechaCreacionGreaterThanEqualAndFechaCreacionLessThan(
                any(), any(), any(), any())).thenReturn(true);
        when(lector.buscar(any(), any(), any(), any(), any()))
                .thenReturn(ResultadoLectura.error("caída", false));

        servicio.consultar(ebay, consulta);

        verify(notificaciones, never()).avisarRol(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void maximoOchoFichasYPrecioVacioNoEsCero() {
        List<FichaFuente> muchas = IntStream.range(0, 12)
                .mapToObj(i -> ficha("Pieza " + i, i == 0 ? "" : "10.00", "https://ebay.example/" + i))
                .toList();
        when(lector.buscar(any(), any(), any(), any(), any())).thenReturn(ResultadoLectura.ok(muchas));

        ConsultaFuente r = servicio.consultar(ebay, consulta);

        assertThat(r.estado()).isEqualTo(EstadoConsultaFuente.OK);
        assertThat(r.fichas()).hasSize(8);
        assertThat(r.fichas().get(0).precio()).isNull();
        assertThat(r.fichas().get(1).precio()).isEqualByComparingTo("10.00");
        assertThat(r.fichas().get(0).codigoMoneda()).isEqualTo("USD");
        assertThat(r.fichas().get(0).titulo()).isEqualTo("Pieza 0");
        assertThat(r.fichas().get(0).urlReferencia()).startsWith("https://ebay.example/");
        assertThat(r.fichas().get(0).urlImagen()).isEqualTo("https://img.example/1.jpg");
        assertThat(r.fichas().get(0).descripcion()).isEqualTo("desc");
    }

    @Test
    void consultarUrl_stubP2_noMarcaConError() {
        when(lector.leerUrl(any(), any(), any(), any()))
                .thenReturn(ResultadoLectura.noConectado(LectorFuenteNoConectado.MENSAJE));

        ConsultaFuente r = servicio.consultarUrl(ebay, "https://www.ebay.com/itm/1", consulta);

        assertThat(r.estado()).isEqualTo(EstadoConsultaFuente.NO_CONECTADO);
        assertThat(ebay.getEstado()).isEqualTo(EstadoFuente.ACTIVA);
        verify(fuenteRepo, never()).save(any());
        verify(notificaciones, never()).avisarRol(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void stubP2_noMarcaConErrorNiAvisa() {
        when(lector.buscar(any(), any(), any(), any(), any()))
                .thenReturn(ResultadoLectura.noConectado("El servicio de lectura todavía no está conectado; no se consulta la tienda."));

        ConsultaFuente r = servicio.consultar(ebay, consulta);

        assertThat(r.estado()).isEqualTo(EstadoConsultaFuente.NO_CONECTADO);
        assertThat(ebay.getEstado()).isEqualTo(EstadoFuente.ACTIVA);
        assertThat(ebay.getUltimoError()).isNull();
        verify(fuenteRepo, never()).save(any());
        verify(notificaciones, never()).avisarRol(any(), any(), any(), any(), any(), anyBoolean());
        verify(lector, times(1)).buscar(any(), any(), any(), any(), any());
    }

    @Test
    void duplicadosPorEnlace_seDescartan() {
        when(lector.buscar(any(), any(), any(), any(), any())).thenReturn(ResultadoLectura.ok(List.of(
                ficha("A", "1", "https://ebay.example/x"),
                ficha("B", "2", "https://ebay.example/x"),
                ficha("C", "3", "https://ebay.example/y"))));

        ConsultaFuente r = servicio.consultar(ebay, consulta);

        assertThat(r.fichas()).hasSize(2);
        assertThat(r.fichas()).extracting(ResultadoBusqueda::titulo).containsExactly("A", "C");
    }

    @Test
    void noGuardaHistorial_elNueveCNoPersisteDesdeAqui() {
        when(lector.buscar(any(), any(), any(), any(), any())).thenReturn(ResultadoLectura.ok(List.of(
                ficha("Filtro", "18.50", "https://ebay.example/1"))));

        ConsultaFuente r = servicio.consultar(ebay, consulta);

        assertThat(r.estado()).isEqualTo(EstadoConsultaFuente.OK);
        assertThat(r.fichas()).hasSize(1);
        verify(fuenteRepo, never()).save(any());
    }
}
