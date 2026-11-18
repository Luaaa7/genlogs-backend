package com.genlogs.app.sourcing.dto;

import java.util.List;

/**
 * Respuesta de POST /api/sourcing/buscar.
 *
 * @param reutilizada          true = no se consultaron fuentes; son referencias del historial reciente
 * @param fuentesConsultadas   códigos de las fuentes que se consultaron
 * @param fuentesConError      fuentes que fallaron (la búsqueda sigue con las demás)
 * @param fuentesOmitidas      fuentes omitidas por cuota diaria o por no tener sesión vigente
 * @param fuentesBloqueadas    fuentes cuya tienda bloqueó la consulta
 * @param fuentesSinResultados fuentes que respondieron sin ninguna ficha
 * @param fuentesSinLector     códigos que se saltaron porque P2 no está conectado (no es caída)
 * @param sugerirUrl           true = no hay referencias: FE-D muestra "Pegar URL" (BPMN 1)
 * @param referencias          referencias encontradas o reutilizadas
 */
public record ResultadoBusquedaResponse(
        boolean reutilizada,
        List<String> fuentesConsultadas,
        List<String> fuentesConError,
        List<String> fuentesOmitidas,
        List<String> fuentesBloqueadas,
        List<String> fuentesSinResultados,
        List<String> fuentesSinLector,
        boolean sugerirUrl,
        List<ReferenciaResponse> referencias) {
}