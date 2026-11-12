package com.genlogs.app.fuentes;

/**
 * La fuente no se pudo consultar: sin red, cuota agotada, credenciales rechazadas o
 * error de la plataforma. SourcingService la atrapa, registra el motivo y sigue con
 * las demás fuentes (o con la captura por URL). El mensaje nunca lleva credenciales.
 */
public class FuenteNoDisponibleException extends RuntimeException {

    public FuenteNoDisponibleException(String motivo) {
        super(motivo);
    }
}
