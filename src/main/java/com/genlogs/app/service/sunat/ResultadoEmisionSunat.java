package com.genlogs.app.service.sunat;

/**
 * Respuesta de SUNAT (o de la simulación) al emitir un comprobante.
 *
 * @param aceptado true si SUNAT lo aceptó (en simulación: si pasó las validaciones)
 * @param estado   ACEPTADA / RECHAZADA
 * @param mensaje  texto para el usuario (en real, el de la constancia CDR)
 */
public record ResultadoEmisionSunat(boolean aceptado, String estado, String mensaje) {

    public static ResultadoEmisionSunat aceptado(String mensaje) {
        return new ResultadoEmisionSunat(true, "ACEPTADA", mensaje);
    }

    public static ResultadoEmisionSunat rechazado(String mensaje) {
        return new ResultadoEmisionSunat(false, "RECHAZADA", mensaje);
    }
}