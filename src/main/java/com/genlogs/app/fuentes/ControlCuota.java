package com.genlogs.app.fuentes;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Contador diario de una fuente (hora de Lima). No guarda el límite: cada
 * {@link #reservar(int)} trae el límite que esa búsqueda acaba de leer.
 * También pausa la fuente el resto del día cuando la plataforma responde 429
 * o rechaza las credenciales.
 *
 * <p>El conteo vive en memoria: se reinicia si la aplicación se reinicia. Con una sola
 * instancia en Render es suficiente; si se escala, habría que llevarlo a la BD.
 */
public class ControlCuota {

    static final ZoneId ZONA = ZoneId.of("America/Lima");

    private final String fuente;
    private final Clock reloj;

    private LocalDate dia;
    private int usadas;
    private String pausaMotivo;

    public ControlCuota(String fuente, Clock reloj) {
        this.fuente = fuente;
        this.reloj = reloj;
    }

    /**
     * Reserva una consulta contra {@code limite} o lanza {@link FuenteNoDisponibleException}.
     * El límite no se guarda: la próxima reserva vuelve a recibirlo.
     */
    public synchronized void reservar(int limite) {
        if (limite < 1) {
            throw new IllegalArgumentException("El límite diario de " + fuente + " debe ser al menos 1");
        }
        cambiarDiaSiCorresponde();
        if (pausaMotivo != null) {
            throw new FuenteNoDisponibleException(fuente + " en pausa hasta mañana: " + pausaMotivo);
        }
        if (usadas >= limite) {
            throw new FuenteNoDisponibleException(
                    fuente + ": cuota diaria agotada (" + limite + " consultas); no se consulta hasta mañana");
        }
        usadas++;
    }

    /** No volver a consultar la fuente hasta mañana. */
    public synchronized void pausarHastaManana(String motivo) {
        cambiarDiaSiCorresponde();
        pausaMotivo = motivo;
    }

    public synchronized int usadasHoy() {
        cambiarDiaSiCorresponde();
        return usadas;
    }

    private void cambiarDiaSiCorresponde() {
        LocalDate hoy = LocalDate.now(reloj.withZone(ZONA));
        if (!hoy.equals(dia)) {
            dia = hoy;
            usadas = 0;
            pausaMotivo = null;
        }
    }
}
