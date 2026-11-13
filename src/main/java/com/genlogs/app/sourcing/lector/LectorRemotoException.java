package com.genlogs.app.sourcing.lector;

import com.genlogs.app.sourcing.lector.ContratoLector.Estado;

/** Mensajes controlados; no incorpora cuerpos HTTP, URLs, tokens ni causas con credenciales. */
public class LectorRemotoException extends RuntimeException {
    private final Estado estado;

    public LectorRemotoException(Estado estado) {
        super(switch (estado) {
            case VERIFICACION -> "La tienda requiere verificación manual.";
            case BLOQUEO -> "La tienda bloqueó la lectura.";
            case SESION_VENCIDA -> "La sesión de la fuente venció; debe renovarse.";
            case TIEMPO_AGOTADO -> "Se agotó el tiempo máximo del lector.";
            case NO_CONFIGURADO -> "El servicio de lectura no está configurado.";
            case NO_AUTORIZADO -> "El token interno del lector fue rechazado.";
            case URL_NO_PERMITIDA -> "La URL no está permitida para esta fuente.";
            case REGLAS_INVALIDAS -> "Las reglas no permiten extraer los datos de la página.";
            case SOLICITUD_INVALIDA -> "La solicitud al lector no cumple el contrato.";
            case RESPUESTA_INVALIDA -> "El lector devolvió una respuesta inválida.";
            case INTERRUMPIDO -> "La lectura fue interrumpida.";
            default -> "El servicio de lectura no responde; se puede continuar con la captura manual.";
        });
        this.estado = estado;
    }

    public Estado getEstado() { return estado; }
}
