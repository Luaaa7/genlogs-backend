package com.genlogs.app.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.genlogs.app.dto.TipoCambioDia;

/**
 * Lee el tipo de cambio para guardarlo en la cotización. No consulta Factiliza:
 * usa {@link TipoCambioService#delDia()}, que ya devuelve el último valor si hoy no hay fila.
 */
@Component
public class TipoCambioCotizacion {

    private static final Logger log = LoggerFactory.getLogger(TipoCambioCotizacion.class);

    private final TipoCambioService tipoCambioService;

    public TipoCambioCotizacion(TipoCambioService tipoCambioService) {
        this.tipoCambioService = tipoCambioService;
    }

    /** Valor a persistir. Null si no hay ninguno o si la lectura falla: el alta sigue. */
    public BigDecimal vigente() {
        try {
            Optional<TipoCambioDia> dia = tipoCambioService.delDia();
            if (dia.isEmpty() || dia.get().valor() == null) {
                log.warn("No hay tipo de cambio guardado; la cotización queda sin tipo_cambio_usado");
                return null;
            }
            if (!dia.get().esDeHoy()) {
                log.warn("No hay tipo de cambio de hoy; se usa el último disponible del {}", dia.get().fecha());
            }
            return dia.get().valor();
        } catch (RuntimeException e) {
            log.warn("No se pudo leer el tipo de cambio; la cotización se guarda igual: {}", e.getMessage());
            return null;
        }
    }
}
