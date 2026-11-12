package com.genlogs.app.vitrina.dto;

import com.genlogs.app.enums.TipoRepuesto;

import java.math.BigDecimal;

/**
 * Repuesto en "Repuestos" del portal (GET /api/portal/repuestos): con precio
 * referencial en soles y plazo. Nunca con fuente, enlace ni precio original (D-13).
 */
public record RepuestoPortalResponse(
        Long idProducto,
        String nombre,
        String descripcion,
        String marca,
        String numeroParte,
        Integer idCategoria,
        String categoria,
        String unidad,
        TipoRepuesto tipoRepuesto,
        String plazoEntrega,
        String urlImagen,
        BigDecimal precioReferencial,
        String moneda) {
}
