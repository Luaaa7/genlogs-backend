package com.genlogs.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Porcentaje de cotizaciones dentro del plazo. No toca la base de datos:
 * sin solicitud o sin fecha límite la cotización no entra.
 * Está dentro del plazo si su fecha no es posterior al día del límite.
 */
public final class CalculoPlazo {

    private CalculoPlazo() {
    }

    public static ResultadoPlazo calcular(List<PlazoDeCotizacion> cotizaciones, BigDecimal meta) {
        int consideradas = 0;
        int enPlazo = 0;
        if (cotizaciones != null) {
            for (PlazoDeCotizacion cotizacion : cotizaciones) {
                if (cotizacion == null || cotizacion.fechaLimite() == null || cotizacion.fechaCotizacion() == null) {
                    continue;
                }
                consideradas++;
                if (!cotizacion.fechaCotizacion().isAfter(cotizacion.fechaLimite())) {
                    enPlazo++;
                }
            }
        }
        if (consideradas == 0) {
            return new ResultadoPlazo(0, 0, null, null);
        }
        BigDecimal porcentaje = BigDecimal.valueOf(enPlazo)
                .multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(consideradas), 2, RoundingMode.HALF_UP);
        Boolean cumple = porcentaje.compareTo(meta) >= 0;
        return new ResultadoPlazo(enPlazo, consideradas, porcentaje, cumple);
    }

    /** Una cotización del mes. fechaLimite null: no se considera. */
    public record PlazoDeCotizacion(LocalDate fechaCotizacion, LocalDate fechaLimite) {
    }

    public record ResultadoPlazo(int enPlazo, int consideradas, BigDecimal porcentaje, Boolean cumple) {
    }
}
