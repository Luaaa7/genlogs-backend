package com.genlogs.app.fuentes;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Cuota diaria de una fuente (hora de Lima). Cuenta las consultas del día y, al llegar
 * al límite, deja de consultar hasta el día siguiente. También pausa la fuente el resto
 * del día cuando la plataforma responde 429 (límite propio) o rechaza las credenciales.
 *
 * <p>El conteo vive en memoria: se reinicia si la aplicación se reinicia. Con una sola
 * instancia en Render es suficiente; si se escala, habría que llevarlo a la BD.
 */
public class ControlCuota {

    static final ZoneId ZONA = ZoneId.of("America/Lima");

    private final String fuente;
    private final int limiteDiario;
    private final Clock reloj;

    private LocalDate dia;
    private int usadas;
    private String pausaMotivo;

    public ControlCuota(String fuente, int limiteDiario, Clock reloj) {
        if (limiteDiario < 1) {
            throw new IllegalArgumentException("El límite diario de " + fuente + " debe ser al menos 1");
        }
        this.fuente = fuente;
        this.limiteDiario = limiteDiario;
        this.reloj = reloj;
    }

    /** Reserva una consulta o lanza {@link FuenteNoDisponibleException} con el motivo. */
    public synchronized void reservar() {
        cambiarDiaSiCorresponde();
        if (pausaMotivo != null) {
            throw new FuenteNoDisponibleException(fuente + " en pausa hasta mañana: " + pausaMotivo);
        }
        if (usadas >= limiteDiario) {
            throw new FuenteNoDisponibleException(
                    fuente + ": cuota diaria agotada (" + limiteDiario + " consultas); no se consulta hasta mañana");
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
