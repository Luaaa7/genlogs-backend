package com.genlogs.app.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.genlogs.app.enums.OrigenRepuesto;
import com.genlogs.app.exception.BusinessException;

/**
 * Plazo de entrega. Nacional: 24 a 48 horas. Importado: 5 a 7 días de lunes a viernes,
 * sin contar los feriados que ya registró BE-B. El día de inicio no cuenta.
 * La fecha que entra se lee como hora de Lima; las pruebas la fijan y no usan el reloj.
 */
@Component
public class PlazoDespacho {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final FeriadoService feriadoService;

    public PlazoDespacho(FeriadoService feriadoService) {
        this.feriadoService = feriadoService;
    }

    public Rango calcular(OrigenRepuesto origen, LocalDateTime inicio) {
        if (inicio == null) {
            throw new BusinessException("Indica la fecha de inicio del plazo");
        }
        if (origen == OrigenRepuesto.NACIONAL) {
            return new Rango(inicio.plusHours(24), inicio.plusHours(48));
        }
        return new Rango(habil(inicio.toLocalDate(), 5), habil(inicio.toLocalDate(), 7));
    }

    private LocalDateTime habil(LocalDate inicio, int dias) {
        LocalDate dia = inicio;
        int contados = 0;
        while (contados < dias) {
            dia = dia.plusDays(1);
            if (esHabil(dia)) {
                contados++;
            }
        }
        return dia.atStartOfDay();
    }

    private boolean esHabil(LocalDate dia) {
        DayOfWeek semana = dia.getDayOfWeek();
        return semana != DayOfWeek.SATURDAY
                && semana != DayOfWeek.SUNDAY
                && !feriadoService.esFeriado(dia);
    }

    public record Rango(LocalDateTime desde, LocalDateTime hasta) {
    }
}
