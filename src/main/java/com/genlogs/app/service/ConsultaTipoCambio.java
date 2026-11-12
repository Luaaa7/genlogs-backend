package com.genlogs.app.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Venta del dólar en Factiliza para una fecha. Las pruebas inyectan un doble
 * y no salen a la red.
 */
public interface ConsultaTipoCambio {

    /** Soles por 1 dólar (valor de venta). Lanza si no hay llave, falla la consulta o el valor no sirve. */
    BigDecimal ventaDelDia(LocalDate fecha);
}
