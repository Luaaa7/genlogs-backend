package com.genlogs.app.exception;

/** El registro del portal superó el límite básico de intentos. */
public class DemasiadasSolicitudesException extends RuntimeException {

    public DemasiadasSolicitudesException() {
        super("No se pudo completar el registro en este momento. Intenta más tarde.");
    }
}
