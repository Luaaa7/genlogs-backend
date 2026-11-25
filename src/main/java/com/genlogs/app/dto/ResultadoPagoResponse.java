package com.genlogs.app.dto;

import java.math.BigDecimal;

/**
 * Resultado de verificar un pago en línea.
 * estado: REGISTRADO, YA_REGISTRADO, PENDIENTE, RECHAZADO, REVISION o IGNORADO.
 */
public record ResultadoPagoResponse(
        String estado,
        String mensaje,
        Long idComprobante,
        String comprobante,
        BigDecimal monto,
        BigDecimal saldoPendiente) {
}