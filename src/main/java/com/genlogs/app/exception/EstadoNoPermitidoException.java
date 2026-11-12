package com.genlogs.app.exception;

/**
 * La acción no se puede hacer en el estado actual del registro (p. ej. cancelar un pedido
 * ya cotizado o pausar uno cancelado). Responde 409 con el mensaje.
 */
public class EstadoNoPermitidoException extends RuntimeException {

    public EstadoNoPermitidoException(String mensaje) {
        super(mensaje);
    }
}
