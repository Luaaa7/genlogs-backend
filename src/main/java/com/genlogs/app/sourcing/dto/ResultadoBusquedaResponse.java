package com.genlogs.app.sourcing.dto;

import java.util.List;

/**
 * Respuesta de POST /api/sourcing/buscar.
 *
 * @param reutilizada        true = no se consultaron fuentes; son referencias de las últimas 24 h
 * @param fuentesConsultadas códigos de las fuentes que respondieron
 * @param fuentesConError    fuentes que fallaron (la búsqueda sigue con las demás)
 * @param sugerirUrl         true = no hay referencias: FE-D muestra "Pegar URL" (BPMN 1)
 */
public record ResultadoBusquedaResponse(
        boolean reutilizada,
        List<String> fuentesConsultadas,
        List<String> fuentesConError,
        boolean sugerirUrl,
        List<ReferenciaResponse> referencias) {
}
