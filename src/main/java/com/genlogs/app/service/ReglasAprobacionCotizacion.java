package com.genlogs.app.service;

import com.genlogs.app.enums.CodigoEstadoCotizacion;
import com.genlogs.app.exception.BusinessException;

/** Transiciones de la aprobación interna. No abre EN_NEGOCIACION, APROBADA, RECHAZADA, VENCIDA ni ANULADA. */
public final class ReglasAprobacionCotizacion {

    public static final String SOLO_BORRADOR =
            "Solo un borrador se envía a aprobación del Jefe";
    public static final String SOLO_PENDIENTE =
            "Solo se aprueba o devuelve una cotización pendiente de aprobación";
    public static final String MOTIVO_OBLIGATORIO =
            "Indica el motivo para devolver la cotización";
    public static final String SOLO_APROBADA_INTERNA =
            "Solo se envía una cotización aprobada por el Jefe";
    public static final String POR_ENDPOINT =
            "Ese estado se cambia con solicitar aprobación, aprobar o devolver";
    public static final String BORRADOR_NO_PASA_A_ENVIADA =
            "Un borrador no pasa directo a enviada: primero lo aprueba el Jefe";

    private ReglasAprobacionCotizacion() {
    }

    public static void exigir(String actual, CodigoEstadoCotizacion esperado, String mensaje) {
        if (actual == null || !esperado.name().equalsIgnoreCase(actual.trim())) {
            throw new BusinessException(mensaje);
        }
    }

    public static void exigirMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException(MOTIVO_OBLIGATORIO);
        }
    }

    /** cambiarEstado sigue para el resto de estados. Estas rutas quedan en sus endpoints. */
    public static void validarCambioLibre(String actual, String destino) {
        String codigo = destino == null ? "" : destino.trim();
        if (es(codigo, CodigoEstadoCotizacion.PENDIENTE_APROBACION)
                || es(codigo, CodigoEstadoCotizacion.APROBADA_INTERNA)) {
            throw new BusinessException(POR_ENDPOINT);
        }
        if (es(actual, CodigoEstadoCotizacion.BORRADOR) && es(codigo, CodigoEstadoCotizacion.ENVIADA)) {
            throw new BusinessException(BORRADOR_NO_PASA_A_ENVIADA);
        }
        if (es(actual, CodigoEstadoCotizacion.PENDIENTE_APROBACION)) {
            throw new BusinessException(SOLO_PENDIENTE);
        }
    }

    private static boolean es(String codigo, CodigoEstadoCotizacion esperado) {
        return codigo != null && esperado.name().equalsIgnoreCase(codigo.trim());
    }
}
