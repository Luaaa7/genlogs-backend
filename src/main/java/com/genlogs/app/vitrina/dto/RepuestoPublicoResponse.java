package com.genlogs.app.vitrina.dto;

import com.genlogs.app.enums.TipoRepuesto;

/**
 * Repuesto en la landing (GET /api/public/catalogo/repuestos). Sin precio (RF-82)
 * y nunca con fuente, enlace ni precio original (D-13).
 *
 * @param urlImagen foto propia verificada, o null: la pantalla muestra el ícono de la categoría
 */
public record RepuestoPublicoResponse(
        Long idProducto,
        String nombre,
        String descripcion,
        String marca,
        String numeroParte,
        Integer idCategoria,
        String categoria,
        TipoRepuesto tipoRepuesto,
        String plazoEntrega,
        String urlImagen) {
}
