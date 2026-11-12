package com.genlogs.app.vitrina.dto;

import com.genlogs.app.enums.EstadoPublicacion;
import com.genlogs.app.enums.OrigenRepuesto;
import com.genlogs.app.enums.TipoRepuesto;
import com.genlogs.app.vitrina.FuenteImagen;
import com.genlogs.app.vitrina.MargenRepuesto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Vista interna del producto de la vitrina (FE-D, pantalla "Publicar"). Trae costo
 * y margen: solo para Gerente, Jefe Adm. y Asistente Logístico. Nunca va al público.
 *
 * @param requiereJefe true si el margen está fuera de rango: solo el Jefe Adm. lo publica (D-26)
 */
public record ProductoVitrinaResponse(
        Long idProducto,
        String codigo,
        String nombre,
        String descripcion,
        Integer idCategoria,
        String categoria,
        Integer idUnidadMedida,
        String unidad,
        String marca,
        String numeroParte,
        TipoRepuesto tipoRepuesto,
        OrigenRepuesto origen,
        EstadoPublicacion estado,
        BigDecimal costoPuestoLima,
        BigDecimal margen,
        BigDecimal precioReferencial,
        MargenRepuesto.Evaluacion evaluacionMargen,
        boolean requiereJefe,
        boolean tieneImagenVerificada,
        LocalDateTime fechaUltimaVerificacion,
        List<Imagen> imagenes) {

    public record Imagen(Long idImagen, String url, FuenteImagen fuente, boolean principal, boolean verificada) {
    }
}
