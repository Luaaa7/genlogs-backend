package com.genlogs.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.model.Cotizacion;

/**
 * Aviso informativo al Gerente cuando el jefe aprueba una cotización de alto valor.
 * No bloquea la aprobación: si el parámetro o el correo fallan, queda un log.
 */
@Component
public class AvisoAltoValorCotizacion {

    private static final Logger log = LoggerFactory.getLogger(AvisoAltoValorCotizacion.class);
    private static final String PEN = "PEN";
    private static final String USD = "USD";

    private final ParametrosService parametrosService;
    private final NotificacionService notificacionService;

    public AvisoAltoValorCotizacion(ParametrosService parametrosService, NotificacionService notificacionService) {
        this.parametrosService = parametrosService;
        this.notificacionService = notificacionService;
    }

    public void alAprobar(Cotizacion cotizacion, BigDecimal total) {
        if (cotizacion == null || Boolean.TRUE.equals(cotizacion.getAvisoAltoValorEnviado())) {
            return;
        }
        try {
            BigDecimal soles = aSoles(cotizacion, total);
            if (soles == null) {
                return;
            }
            BigDecimal umbral = parametrosService.getDecimal(ClavesParametro.MONTO_ALTO_VALOR_PEN);
            if (soles.compareTo(umbral) <= 0) {
                return;
            }
            String codigo = cotizacion.getCodigoCotizacion() != null
                    ? cotizacion.getCodigoCotizacion()
                    : String.valueOf(cotizacion.getIdCotizacion());
            notificacionService.avisarRol(
                    RolSistema.GERENTE_GENERAL,
                    TipoAviso.ALTO_VALOR,
                    "Cotización de alto valor",
                    codigo + " por S/ " + soles.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    "/cotizaciones/" + cotizacion.getIdCotizacion(),
                    true);
            cotizacion.setAvisoAltoValorEnviado(true);
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar el alto valor de la cotización {}: {}",
                    cotizacion.getIdCotizacion(), e.getMessage());
        }
    }

    /** Total en soles. Null si falta el tipo de cambio de una cotización en USD o la moneda no es PEN/USD. */
    static BigDecimal aSoles(Cotizacion cotizacion, BigDecimal total) {
        if (total == null || cotizacion.getMoneda() == null || cotizacion.getMoneda().getCodigoMoneda() == null) {
            log.warn("Cotización {} sin total o moneda; no se evalúa el alto valor", cotizacion.getIdCotizacion());
            return null;
        }
        String moneda = cotizacion.getMoneda().getCodigoMoneda().trim();
        if (PEN.equalsIgnoreCase(moneda)) {
            return total;
        }
        if (USD.equalsIgnoreCase(moneda)) {
            if (cotizacion.getTipoCambioUsado() == null) {
                log.warn("Cotización {} en USD sin tipo_cambio_usado; no se avisa alto valor", cotizacion.getIdCotizacion());
                return null;
            }
            return total.multiply(cotizacion.getTipoCambioUsado());
        }
        log.warn("Cotización {} en {} no se convierte a soles para el aviso", cotizacion.getIdCotizacion(), moneda);
        return null;
    }
}
