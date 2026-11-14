package com.genlogs.app.sourcing.extraccion;

import java.math.BigDecimal;

import com.genlogs.app.sourcing.ConsultaBusqueda;
import com.genlogs.app.sourcing.ResultadoBusqueda;

/**
 * Una ficha ya limpia y normalizada con los 6 campos (A4, v3.3).
 * Se pasa a {@link ResultadoBusqueda} para no cambiar el contrato de los lectores;
 * la descripción queda aquí para quien la guarde (historial_sourcing.descripcion).
 *
 * @param precio null si la tienda no muestra precio (vacío, nunca 0)
 */
public record FichaExtraida(
        String nombre,
        String descripcion,
        BigDecimal precio,
        String codigoMoneda,
        String urlImagen,
        String enlace) {

    /** El resultado que devuelve un lector; la marca y el N.º de parte salen de lo que se buscó. */
    public ResultadoBusqueda aResultado(ConsultaBusqueda consulta) {
        return new ResultadoBusqueda(nombre,
                consulta != null ? consulta.marca() : null,
                consulta != null ? consulta.numeroParte() : null,
                precio, codigoMoneda, enlace, urlImagen);
    }
}