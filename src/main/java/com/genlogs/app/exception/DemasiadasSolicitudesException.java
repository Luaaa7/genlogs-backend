package com.genlogs.app.exception;

/** Se superó un límite básico de intentos (registro del portal o envío de solicitudes). Responde 429. */
public class DemasiadasSolicitudesException extends RuntimeException {

    public DemasiadasSolicitudesException() {
        super("No se pudo completar el registro en este momento. Intenta más tarde.");
    }

    public DemasiadasSolicitudesException(String mensaje) {
        super(mensaje);
    }
}
