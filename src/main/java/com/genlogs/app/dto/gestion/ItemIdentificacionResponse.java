package com.genlogs.app.dto.gestion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Repuesto de la solicitud visto por el equipo interno, con la identificación confirmada. */
public record ItemIdentificacionResponse(
        Long idItem,
        String descripcion,
        BigDecimal cantidad,
        String unidad,
        Integer idMarca,
        String marca,
        String numeroParte,
        String gtin,
        String urlFoto,
        Long idProducto,
        List<String> equivalentes,
        boolean confirmado,
        String confirmadoPor,
        LocalDateTime fechaConfirmacion) {
}
