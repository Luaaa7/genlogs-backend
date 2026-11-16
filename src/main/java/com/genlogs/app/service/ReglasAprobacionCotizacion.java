package com.genlogs.app.service;

import com.genlogs.app.enums.CodigoEstadoCotizacion;
import com.genlogs.app.exception.BusinessException;

/** Transiciones libres del PATCH /estado. El resto queda en su endpoint. */
public final class ReglasAprobacionCotizacion {

    public static final String SOLO_BORRADOR =
            "Solo un borrador se envía a aprobación del Jefe";
    public static final String SOLO_PENDIENTE =
            "Solo se aprueba o devuelve una cotización pendiente de aprobación";
    public static final String MOTIVO_OBLIGATORIO =
            "Indica el motivo para devolver la cotización";
    public static final String SOLO_APROBADA_INTERNA =
            "Solo se envía una cotización aprobada por el Jefe";
    public static final String APROBACION_INTERNA_OBLIGATORIA =
            "Ese cambio exige la aprobación interna: usa solicitar aprobación, aprobar, devolver o enviar";
    public static final String DESTINO_NO_PERMITIDO =
            "Ese cambio de estado no está permitido";
    public static final String MOTIVO_ANULACION_OBLIGATORIO =
            "Indica el motivo para anular la cotización";

    /**
     * TODO equipo: el Word dice "pasar a ANULADA con motivo" y no lista el estado de origen.
     * true = desde cualquier estado que no sea final. El final lo rechaza
     * CotizacionService.cambiarEstado con esFinal. Para acotarlo, pasar esto a false.
     */
    static final boolean ANULAR_DESDE_CUALQUIER_NO_FINAL = true;

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

    /**
     * PATCH /estado solo admite ENVIADA → EN_NEGOCIACION, o ANULADA con motivo
     * desde un estado no final. Lo demás queda en su endpoint o se rechaza.
     */
    public static void validarCambioLibre(String actual, String destino, String motivo) {
        String codigo = destino == null ? "" : destino.trim();
        if (es(codigo, CodigoEstadoCotizacion.PENDIENTE_APROBACION)
                || es(codigo, CodigoEstadoCotizacion.APROBADA_INTERNA)
                || (es(actual, CodigoEstadoCotizacion.BORRADOR)
                        && es(codigo, CodigoEstadoCotizacion.ENVIADA))) {
            throw new BusinessException(APROBACION_INTERNA_OBLIGATORIA);
        }
        if (es(codigo, CodigoEstadoCotizacion.ANULADA)) {
            if (!ANULAR_DESDE_CUALQUIER_NO_FINAL) {
                throw new BusinessException(DESTINO_NO_PERMITIDO);
            }
            if (motivo == null || motivo.isBlank()) {
                throw new BusinessException(MOTIVO_ANULACION_OBLIGATORIO);
            }
            return;
        }
        if (es(actual, CodigoEstadoCotizacion.ENVIADA)
                && es(codigo, CodigoEstadoCotizacion.EN_NEGOCIACION)) {
            return;
        }
        throw new BusinessException(DESTINO_NO_PERMITIDO);
    }

    private static boolean es(String codigo, CodigoEstadoCotizacion esperado) {
        return codigo != null && esperado.name().equalsIgnoreCase(codigo.trim());
    }
}
