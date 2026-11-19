package com.genlogs.app.exception;

/** Se superó un límite básico de intentos (registro del portal o envío de solicitudes). Responde 429. */
public class DemasiadasSolicitudesException extends RuntimeException {

    public DemasiadasSolicitudesException() {
        super("Demasiados intentos con este RUC o correo. Espera 15 minutos o prueba con otro RUC y otro correo.");
    }

    public DemasiadasSolicitudesException(String mensaje) {
        super(mensaje);
    }
}
