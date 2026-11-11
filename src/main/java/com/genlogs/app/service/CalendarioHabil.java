package com.genlogs.app.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.function.Predicate;

/**
 * Calcula la fecha límite interna de una solicitud en días hábiles.
 *
 * Horario de GENLOGS: lunes a viernes 08:00-18:00 y sábado 08:00-13:00; domingo cerrado.
 * El plazo empieza a contar el día hábil siguiente a la recepción y vence al cierre
 * del N-ésimo día hábil. Lo recibido después del cierre (o en un día sin atención)
 * cuenta como recibido en la siguiente apertura.
 *
 * Los feriados registrados (D-18) no cuentan como días hábiles.
 */
@Component
public class CalendarioHabil {

    private static final LocalTime CIERRE_SEMANA = LocalTime.of(18, 0);
    private static final LocalTime CIERRE_SABADO = LocalTime.of(13, 0);

    private final Predicate<LocalDate> esFeriado;

    /** Sin feriados (para pruebas y cálculos que no los necesitan). */
    public CalendarioHabil() {
        this(fecha -> false);
    }

    /** El que usa Spring: toma los feriados registrados en la tabla feriado. */
    @Autowired
    public CalendarioHabil(FeriadoService feriadoService) {
        this(feriadoService::esFeriado);
    }

    public CalendarioHabil(Predicate<LocalDate> esFeriado) {
        this.esFeriado = esFeriado;
    }

    public LocalDateTime fechaLimite(LocalDateTime recibido, int diasHabiles) {
        if (recibido == null) {
            throw new IllegalArgumentException("La fecha de recepción es obligatoria");
        }
        if (diasHabiles < 1) {
            throw new IllegalArgumentException("El plazo debe ser de al menos 1 día hábil");
        }
        LocalDate dia = diaDeRecepcion(recibido);
        for (int i = 0; i < diasHabiles; i++) {
            dia = siguienteDiaHabil(dia);
        }
        return dia.atTime(cierre(dia));
    }

    /** Día hábil en que se considera recibida la solicitud. */
    private LocalDate diaDeRecepcion(LocalDateTime recibido) {
        LocalDate dia = recibido.toLocalDate();
        if (esHabil(dia) && recibido.toLocalTime().isBefore(cierre(dia))) {
            return dia;
        }
        return siguienteDiaHabil(dia);
    }

    private LocalDate siguienteDiaHabil(LocalDate dia) {
        LocalDate siguiente = dia.plusDays(1);
        while (!esHabil(siguiente)) {
            siguiente = siguiente.plusDays(1);
        }
        return siguiente;
    }

    private boolean esHabil(LocalDate dia) {
        return dia.getDayOfWeek() != DayOfWeek.SUNDAY && !esFeriado.test(dia);
    }

    private LocalTime cierre(LocalDate dia) {
        return dia.getDayOfWeek() == DayOfWeek.SATURDAY ? CIERRE_SABADO : CIERRE_SEMANA;
    }
}
