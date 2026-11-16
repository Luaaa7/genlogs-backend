package com.genlogs.app.sourcing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.EstadoFuente;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfig;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfigRepository;
import com.genlogs.app.fuentes.config.FuenteSelectorRepository;
import com.genlogs.app.fuentes.config.LectorFuente;
import com.genlogs.app.fuentes.config.ResultadoLectura;
import com.genlogs.app.fuentes.config.SesionFuenteService;
import com.genlogs.app.repository.NotificacionRepository;
import com.genlogs.app.service.NotificacionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cada consulta pasa el límite que trae su fuente. El contador no se reinicia
 * y una búsqueda no deja su límite pegado para la siguiente.
 */
class ConsultaFuenteCuotaDinamicaTest {

    private SesionFuenteService sesiones;
    private LectorFuente lector;
    private NotificacionService notificaciones;
    private FuenteBusquedaConfigRepository fuenteRepo;
    private ConsultaFuenteService servicio;
    private ConsultaBusqueda consulta;

    @BeforeEach
    void setUp() {
        FuenteSelectorRepository selectores = mock(FuenteSelectorRepository.class);
        sesiones = mock(SesionFuenteService.class);
        lector = mock(LectorFuente.class);
        fuenteRepo = mock(FuenteBusquedaConfigRepository.class);
        notificaciones = mock(NotificacionService.class);
        when(selectores.findByFuente_CodigoAndStatus(any(), eq("A"))).thenReturn(List.of());
        when(sesiones.sesionVigente(any())).thenReturn(Optional.of("sesion-opaca"));
        when(lector.buscar(any(), any(), any(), any(), any())).thenReturn(ResultadoLectura.sinResultados());
        servicio = new ConsultaFuenteService(fuenteRepo, selectores, sesiones, lector,
                mock(com.genlogs.app.sourcing.PausaFuente.class), notificaciones,
                mock(NotificacionRepository.class));
        consulta = new ConsultaBusqueda(MetodoBusqueda.NUMERO_PARTE, "1R-0750", "Caterpillar",
                null, null, null);
    }

    @Test
    void alSubirElLimiteQuedanLasConsultasNoUsadas() {
        FuenteBusquedaConfig ebay = fuente(CodigoFuente.EBAY, 50);
        assertThat(consumir(ebay, 12)).isEqualTo(12);

        ebay.setLimiteDiario(80);

        assertThat(consumirHastaCuota(ebay)).isEqualTo(68);
    }

    @Test
    void alBajarElLimiteSoloQuedaElCupoNuevo() {
        FuenteBusquedaConfig ebay = fuente(CodigoFuente.EBAY, 50);
        assertThat(consumir(ebay, 12)).isEqualTo(12);

        ebay.setLimiteDiario(20);

        assertThat(consumirHastaCuota(ebay)).isEqualTo(8);
    }

    @Test
    void unLimiteAgotadoVuelveAAbrirSiLoSuben() {
        FuenteBusquedaConfig ebay = fuente(CodigoFuente.EBAY, 50);
        assertThat(consumir(ebay, 50)).isEqualTo(50);
        assertThat(servicio.consultar(ebay, consulta).estado()).isEqualTo(EstadoConsultaFuente.OMITIDA_CUOTA);

        ebay.setLimiteDiario(80);

        assertThat(consumirHastaCuota(ebay)).isEqualTo(30);
    }

    @Test
    void cambiarUnaFuenteNoTocaLaCuotaDeOtra() {
        FuenteBusquedaConfig ebay = fuente(CodigoFuente.EBAY, 50);
        FuenteBusquedaConfig amazon = fuente(CodigoFuente.AMAZON, 30);
        assertThat(consumir(ebay, 12)).isEqualTo(12);
        assertThat(consumir(amazon, 5)).isEqualTo(5);

        ebay.setLimiteDiario(80);

        assertThat(consumirHastaCuota(ebay)).isEqualTo(68);
        assertThat(consumirHastaCuota(amazon)).isEqualTo(25);
    }

    @Test
    void elNuevoLimiteRigeEnLaSiguienteConsultaSinReiniciar() {
        FuenteBusquedaConfig ebay = fuente(CodigoFuente.EBAY, 2);
        assertThat(consumir(ebay, 2)).isEqualTo(2);
        assertThat(servicio.consultar(ebay, consulta).estado()).isEqualTo(EstadoConsultaFuente.OMITIDA_CUOTA);

        ebay.setLimiteDiario(3);

        assertThat(servicio.consultar(ebay, consulta).estado()).isEqualTo(EstadoConsultaFuente.SIN_RESULTADOS);
        assertThat(servicio.consultar(ebay, consulta).estado()).isEqualTo(EstadoConsultaFuente.OMITIDA_CUOTA);
    }

    @Test
    @Timeout(10)
    void elConsumoConcurrenteNoPasaDelLimiteVigente() throws Exception {
        FuenteBusquedaConfig ebay = fuente(CodigoFuente.EBAY, 20);
        int hilos = 8;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CyclicBarrier barrera = new CyclicBarrier(hilos);
        AtomicInteger exitosas = new AtomicInteger();
        try {
            List<Future<Void>> tareas = new ArrayList<>();
            for (int h = 0; h < hilos; h++) {
                tareas.add(pool.submit(tarea(barrera, () -> {
                    for (int i = 0; i < 15; i++) {
                        ConsultaFuente r = servicio.consultar(ebay, consulta);
                        if (r.estado() == EstadoConsultaFuente.SIN_RESULTADOS) {
                            exitosas.incrementAndGet();
                        } else {
                            assertThat(r.estado()).isEqualTo(EstadoConsultaFuente.OMITIDA_CUOTA);
                        }
                    }
                })));
            }
            for (Future<Void> tarea : tareas) {
                tarea.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(exitosas.get()).isEqualTo(20);
    }

    @Test
    void unaBusquedaAntiguaNoDejaSuLimiteEnLaSiguiente() {
        FuenteBusquedaConfig antigua = fuente(CodigoFuente.EBAY, 50);
        FuenteBusquedaConfig nueva = fuente(CodigoFuente.EBAY, 20);
        assertThat(consumir(antigua, 12)).isEqualTo(12);

        assertThat(consumirHastaCuota(nueva)).isEqualTo(8);
        assertThat(servicio.consultar(antigua, consulta).estado()).isEqualTo(EstadoConsultaFuente.SIN_RESULTADOS);
        assertThat(servicio.consultar(nueva, consulta).estado()).isEqualTo(EstadoConsultaFuente.OMITIDA_CUOTA);

        FuenteBusquedaConfig subida = fuente(CodigoFuente.EBAY, 80);
        assertThat(servicio.consultar(subida, consulta).estado()).isEqualTo(EstadoConsultaFuente.SIN_RESULTADOS);
    }

    @Test
    @Timeout(10)
    void dosBusquedasConcurrentesRespetanElLimiteDeSuLlamada() throws Exception {
        FuenteBusquedaConfig baja = fuente(CodigoFuente.EBAY, 20);
        FuenteBusquedaConfig alta = fuente(CodigoFuente.EBAY, 50);
        int hilos = 8;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CyclicBarrier barrera = new CyclicBarrier(hilos);
        AtomicInteger conBaja = new AtomicInteger();
        AtomicInteger conAlta = new AtomicInteger();
        try {
            List<Future<Void>> tareas = new ArrayList<>();
            for (int h = 0; h < hilos; h++) {
                FuenteBusquedaConfig fuente = h % 2 == 0 ? baja : alta;
                AtomicInteger contador = h % 2 == 0 ? conBaja : conAlta;
                tareas.add(pool.submit(tarea(barrera, () -> {
                    for (int i = 0; i < 20; i++) {
                        ConsultaFuente r = servicio.consultar(fuente, consulta);
                        if (r.estado() == EstadoConsultaFuente.SIN_RESULTADOS) {
                            contador.incrementAndGet();
                        } else {
                            assertThat(r.estado()).isEqualTo(EstadoConsultaFuente.OMITIDA_CUOTA);
                        }
                    }
                })));
            }
            for (Future<Void> tarea : tareas) {
                tarea.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(conBaja.get()).isLessThanOrEqualTo(20);
        assertThat(conBaja.get() + conAlta.get()).isLessThanOrEqualTo(50);
        assertThat(servicio.consultar(baja, consulta).estado()).isEqualTo(EstadoConsultaFuente.OMITIDA_CUOTA);
        assertThat(servicio.consultar(fuente(CodigoFuente.EBAY, 80), consulta).estado())
                .isEqualTo(EstadoConsultaFuente.SIN_RESULTADOS);
    }

    @Test
    void sinLectorNoConsumeYUnErrorReintentableConsumeUnaVez() {
        FuenteBusquedaConfig ebay = fuente(CodigoFuente.EBAY, 1);
        when(lector.buscar(any(), any(), any(), any(), any()))
                .thenReturn(ResultadoLectura.noConectado("No hay un lector registrado para EBAY; no se consulta la tienda."));

        assertThat(servicio.consultar(ebay, consulta).estado()).isEqualTo(EstadoConsultaFuente.NO_CONECTADO);
        assertThat(servicio.consultar(ebay, consulta).estado()).isEqualTo(EstadoConsultaFuente.NO_CONECTADO);
        verify(fuenteRepo, never()).save(any());
        verify(notificaciones, never()).avisarRol(any(), any(), any(), any(), any(), anyBoolean());

        when(lector.buscar(any(), any(), any(), any(), any()))
                .thenReturn(ResultadoLectura.error("tiempo agotado", true));
        ConsultaFuente error = servicio.consultar(ebay, consulta);

        assertThat(error.estado()).isEqualTo(EstadoConsultaFuente.CON_ERROR);
        verify(lector, times(2 + 2)).buscar(any(), any(), any(), any(), any());
        assertThat(servicio.consultar(ebay, consulta).estado()).isEqualTo(EstadoConsultaFuente.OMITIDA_CUOTA);
        verify(lector, times(4)).buscar(any(), any(), any(), any(), any());
    }

    @Test
    void sinSesionNoConsumeYUnBloqueoSi() {
        FuenteBusquedaConfig ebay = fuente(CodigoFuente.EBAY, 1);
        when(sesiones.sesionVigente(CodigoFuente.EBAY)).thenReturn(Optional.empty());

        assertThat(servicio.consultar(ebay, consulta).estado()).isEqualTo(EstadoConsultaFuente.OMITIDA_SESION);
        verify(lector, never()).buscar(any(), any(), any(), any(), any());

        when(sesiones.sesionVigente(CodigoFuente.EBAY)).thenReturn(Optional.of("sesion-opaca"));
        when(lector.buscar(any(), any(), any(), any(), any()))
                .thenReturn(ResultadoLectura.bloqueada("HTTP 403"));

        assertThat(servicio.consultar(ebay, consulta).estado()).isEqualTo(EstadoConsultaFuente.BLOQUEADA);
        assertThat(ebay.getEstado()).isEqualTo(EstadoFuente.ACTIVA);
        assertThat(servicio.consultar(ebay, consulta).estado()).isEqualTo(EstadoConsultaFuente.OMITIDA_CUOTA);
        verify(lector, times(1)).buscar(any(), any(), any(), any(), any());
    }

    private int consumir(FuenteBusquedaConfig fuente, int n) {
        int hechas = 0;
        for (int i = 0; i < n; i++) {
            if (servicio.consultar(fuente, consulta).estado() == EstadoConsultaFuente.SIN_RESULTADOS) {
                hechas++;
            }
        }
        return hechas;
    }

    /** Consultas que entran después del cambio, hasta la primera omisión por cuota. */
    private int consumirHastaCuota(FuenteBusquedaConfig fuente) {
        int hechas = 0;
        for (int i = 0; i < 100; i++) {
            ConsultaFuente r = servicio.consultar(fuente, consulta);
            if (r.estado() == EstadoConsultaFuente.OMITIDA_CUOTA) {
                return hechas;
            }
            assertThat(r.estado()).isEqualTo(EstadoConsultaFuente.SIN_RESULTADOS);
            hechas++;
        }
        return hechas;
    }

    private static FuenteBusquedaConfig fuente(CodigoFuente codigo, int limite) {
        FuenteBusquedaConfig f = new FuenteBusquedaConfig();
        f.setCodigo(codigo);
        f.setNombre(codigo.name());
        f.setUrlBase("https://" + codigo.name().toLowerCase() + ".example");
        f.setActiva(true);
        f.setEstado(EstadoFuente.ACTIVA);
        f.setLimiteDiario(limite);
        f.setPausaSegundos(0);
        f.setUserCreate("PRUEBA");
        f.setProcessCreate("PRUEBA");
        return f;
    }

    private static Callable<Void> tarea(CyclicBarrier barrera, Runnable cuerpo) {
        return () -> {
            barrera.await();
            cuerpo.run();
            return null;
        };
    }
}
