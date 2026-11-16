package com.genlogs.app.fuentes;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ControlCuotaTest {

    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

    @Test
    void subirElLimiteConservaLoConsumido() {
        ControlCuota cuota = new ControlCuota("EBAY", reloj);
        assertThat(consumir(cuota, 12, 50)).isEqualTo(12);

        assertThat(consumirHastaRechazo(cuota, 80)).isEqualTo(68);
        assertThat(cuota.usadasHoy()).isEqualTo(80);
    }

    @Test
    void bajarElLimiteConservaLoConsumido() {
        ControlCuota cuota = new ControlCuota("EBAY", reloj);
        assertThat(consumir(cuota, 12, 50)).isEqualTo(12);

        assertThat(consumirHastaRechazo(cuota, 20)).isEqualTo(8);
        assertThat(cuota.usadasHoy()).isEqualTo(20);
    }

    @Test
    void alSubirUnLimiteAgotadoQuedaElCupoNuevo() {
        ControlCuota cuota = new ControlCuota("EBAY", reloj);
        assertThat(consumir(cuota, 50, 50)).isEqualTo(50);

        assertThat(consumirHastaRechazo(cuota, 80)).isEqualTo(30);
        assertThat(cuota.usadasHoy()).isEqualTo(80);
    }

    @Test
    void elLimiteDeUnaFuenteNoCambiaElDeOtra() {
        ControlCuota ebay = new ControlCuota("EBAY", reloj);
        ControlCuota amazon = new ControlCuota("AMAZON", reloj);
        assertThat(consumir(ebay, 12, 50)).isEqualTo(12);
        assertThat(consumir(amazon, 5, 30)).isEqualTo(5);

        assertThat(consumirHastaRechazo(ebay, 80)).isEqualTo(68);
        assertThat(amazon.usadasHoy()).isEqualTo(5);
        assertThat(consumirHastaRechazo(amazon, 30)).isEqualTo(25);
    }

    @Test
    void unLimiteInvalidoNoBorraElConteo() {
        ControlCuota cuota = new ControlCuota("EBAY", reloj);
        assertThat(consumir(cuota, 12, 50)).isEqualTo(12);

        assertThatThrownBy(() -> cuota.reservar(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos 1");

        assertThat(cuota.usadasHoy()).isEqualTo(12);
        assertThat(consumirHastaRechazo(cuota, 50)).isEqualTo(38);
    }

    @Test
    void elCambioDeDiaSigueReiniciandoElConteo() {
        RelojMutable relojDia = new RelojMutable(Instant.parse("2026-11-14T18:00:00Z"));
        ControlCuota cuota = new ControlCuota("EBAY", relojDia);
        cuota.reservar(10);
        cuota.pausarHastaManana("429");

        relojDia.avanzarHoras(24);

        assertThat(cuota.usadasHoy()).isZero();
        cuota.reservar(40);
        assertThat(cuota.usadasHoy()).isEqualTo(1);
    }

    @Test
    void unaReservaAntiguaNoDejaSuLimiteEnLasSiguientes() {
        ControlCuota cuota = new ControlCuota("EBAY", reloj);
        assertThat(consumir(cuota, 12, 50)).isEqualTo(12);

        cuota.reservar(50);

        assertThat(cuota.usadasHoy()).isEqualTo(13);
        assertThat(consumirHastaRechazo(cuota, 20)).isEqualTo(7);
        assertThat(cuota.usadasHoy()).isEqualTo(20);
        cuota.reservar(80);
        assertThat(cuota.usadasHoy()).isEqualTo(21);
    }

    @Test
    @Timeout(10)
    void elConsumoConcurrenteNoSuperaElLimiteNiPierdeConteos() throws Exception {
        ControlCuota cuota = new ControlCuota("EBAY", reloj);
        int hilos = 8;
        AtomicInteger concedidas = new AtomicInteger();
        ejecutar(hilos, () -> {
            for (int i = 0; i < 40; i++) {
                try {
                    cuota.reservar(100);
                    concedidas.incrementAndGet();
                } catch (FuenteNoDisponibleException ignorada) {
                    // cupo cubierto
                }
            }
        });

        assertThat(concedidas.get()).isEqualTo(cuota.usadasHoy()).isEqualTo(100);
    }

    @Test
    @Timeout(10)
    void dosLimitesConcurrentesNoSePisan() throws Exception {
        ControlCuota cuota = new ControlCuota("EBAY", reloj);
        int hilos = 8;
        AtomicInteger conVeinte = new AtomicInteger();
        AtomicInteger conCincuenta = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CyclicBarrier barrera = new CyclicBarrier(hilos);
        try {
            List<Future<Void>> tareas = new ArrayList<>();
            for (int h = 0; h < hilos; h++) {
                int limite = h % 2 == 0 ? 20 : 50;
                AtomicInteger contador = h % 2 == 0 ? conVeinte : conCincuenta;
                tareas.add(pool.submit(tarea(barrera, () -> {
                    for (int i = 0; i < 30; i++) {
                        try {
                            cuota.reservar(limite);
                            contador.incrementAndGet();
                        } catch (FuenteNoDisponibleException ignorada) {
                            // este hilo ya no cabe en el límite que pasó
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

        assertThat(conVeinte.get() + conCincuenta.get()).isEqualTo(cuota.usadasHoy());
        assertThat(conVeinte.get()).isLessThanOrEqualTo(20);
        assertThat(cuota.usadasHoy()).isLessThanOrEqualTo(50);
        assertThatThrownBy(() -> cuota.reservar(20)).isInstanceOf(FuenteNoDisponibleException.class);
        cuota.reservar(80);
        assertThat(cuota.usadasHoy()).isGreaterThan(20);
    }

    private static int consumir(ControlCuota cuota, int n, int limite) {
        int hechas = 0;
        for (int i = 0; i < n; i++) {
            cuota.reservar(limite);
            hechas++;
        }
        return hechas;
    }

    /** Cuántas reservas entran con ese límite antes del rechazo. */
    private static int consumirHastaRechazo(ControlCuota cuota, int limite) {
        int concedidas = 0;
        while (concedidas < 200) {
            try {
                cuota.reservar(limite);
                concedidas++;
            } catch (FuenteNoDisponibleException e) {
                return concedidas;
            }
        }
        return concedidas;
    }

    private static void ejecutar(int hilos, Runnable cuerpo) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CyclicBarrier barrera = new CyclicBarrier(hilos);
        try {
            List<Future<Void>> tareas = new ArrayList<>();
            for (int h = 0; h < hilos; h++) {
                tareas.add(pool.submit(tarea(barrera, cuerpo)));
            }
            for (Future<Void> tarea : tareas) {
                tarea.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static Callable<Void> tarea(CyclicBarrier barrera, Runnable cuerpo) {
        return () -> {
            barrera.await();
            cuerpo.run();
            return null;
        };
    }

    private static final class RelojMutable extends Clock {
        private Instant instante;

        private RelojMutable(Instant instante) {
            this.instante = instante;
        }

        private void avanzarHoras(long horas) {
            instante = instante.plus(horas, ChronoUnit.HOURS);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instante, zone);
        }

        @Override
        public Instant instant() {
            return instante;
        }
    }
}
