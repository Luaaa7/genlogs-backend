package com.genlogs.app.sourcing.dto;

import java.util.List;

/**
 * Respuesta de POST /api/sourcing/buscar.
 *
 * @param fuentesSinLector códigos que se saltaron porque P2 no está conectado (no es caída)
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
