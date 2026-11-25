package com.genlogs.app.pagos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Llamadas a la API de Mercado Pago. Existe para que las pruebas simulen la pasarela
 * sin red: la implementación real es {@link MercadoPagoClienteHttp}.
 */
public interface MercadoPagoCliente {

    /** true si hay Access Token configurado (MERCADOPAGO_ACCESS_TOKEN). */
    boolean habilitado();

    /** Crea la preferencia (Checkout Pro) y devuelve el enlace de pago. */
    Preferencia crearPreferencia(PedidoPreferencia pedido);

    /** GET /v1/payments/{id}: el estado real del pago. Nunca se confía en el aviso. */
    Pago consultarPago(String idPago);

    /** Lo que se cobra: un solo ítem por el saldo pendiente del comprobante. */
    record PedidoPreferencia(String referenciaExterna, String titulo, BigDecimal monto, String moneda) {
    }

    record Preferencia(String id, String urlPago) {
    }

    /** status de Mercado Pago: approved, pending, in_process, rejected, cancelled, refunded... */
    record Pago(String id, String estado, String referenciaExterna, BigDecimal monto, String moneda,
            LocalDateTime fechaAprobacion) {

        public boolean aprobado() {
            return "approved".equalsIgnoreCase(estado);
        }

        public boolean pendiente() {
            return "pending".equalsIgnoreCase(estado) || "in_process".equalsIgnoreCase(estado)
                    || "authorized".equalsIgnoreCase(estado);
        }
    }
}