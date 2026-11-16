package com.genlogs.app.sourcing;

import java.util.List;

/**
 * Resultado de consultar una tienda (BPMN 9-B.1). Las fichas ya vienen normalizadas y recortadas a 8.
 */
public record ConsultaFuente(
        String codigo,
        EstadoConsultaFuente estado,
        String motivo,
        List<ResultadoBusqueda> fichas) {

    public static ConsultaFuente de(String codigo, EstadoConsultaFuente estado, String motivo,
                                    List<ResultadoBusqueda> fichas) {
        return new ConsultaFuente(codigo, estado, motivo, fichas == null ? List.of() : List.copyOf(fichas));
    }
}
