package com.genlogs.app.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.genlogs.app.enums.EstadoAprobacionServicio;
import com.genlogs.app.model.CategoriaServicio;
import com.genlogs.app.model.Servicio;
import com.genlogs.app.model.UnidadMedida;

/**
 * S1 (v3.3): lo que devuelven GET /api/servicios y /api/servicios/{id}.
 * Tiene la misma forma que la entidad (el frontend no cambia), pero costo, margen y
 * precioManual solo salen para Gerente General, Jefe Administrativo y Asistente
 * Logístico (matriz v3.2). Para los demás esos campos no vienen en la respuesta.
 * El precio referencial lo ven todos. Es lo que usa el relleno de la cotización (CC).
 */
public record ServicioResponse(
        Long idServicio,
        Categoria categoriaServicio,
        Unidad unidadMedida,
        String codigoServicio,
        String nombreServicio,
        BigDecimal duracionEstimadaHoras,
        Boolean visibleWeb,
        String descripcion,
        @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal costo,
        @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal margen,
        BigDecimal precioReferencial,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean precioManual,
        EstadoAprobacionServicio estadoAprobacion,
        String status,
        String userCreate,
        LocalDateTime dateCreate,
        String userUpdate,
        LocalDateTime dateUpdate) {

    /** Categoría con su padre (un nivel, como la usa el frontend para sugerir el margen). */
    public record Categoria(Integer idCategoriaServicio, String nombreCategoria, String slugWeb, Categoria categoriaPadre) {

        static Categoria de(CategoriaServicio c) {
            return de(c, true);
        }

        private static Categoria de(CategoriaServicio c, boolean conPadre) {
            if (c == null) {
                return null;
            }
            return new Categoria(c.getIdCategoriaServicio(), c.getNombreCategoria(), c.getSlugWeb(),
                    conPadre ? de(c.getCategoriaPadre(), false) : null);
        }
    }

    public record Unidad(Integer idUnidadMedida, String codigoUnidad, String nombreUnidad) {

        static Unidad de(UnidadMedida u) {
            return u == null ? null : new Unidad(u.getIdUnidadMedida(), u.getCodigoUnidad(), u.getNombreUnidad());
        }
    }

    /**
     * @param veCosto true solo para los roles con margen; si es false, costo, margen y
     *                precioManual quedan fuera de la respuesta.
     */
    public static ServicioResponse de(Servicio s, boolean veCosto) {
        if (s == null) {
            return null;
        }
        return new ServicioResponse(
                s.getIdServicio(),
                Categoria.de(s.getCategoriaServicio()),
                Unidad.de(s.getUnidadMedida()),
                s.getCodigoServicio(),
                s.getNombreServicio(),
                s.getDuracionEstimadaHoras(),
                s.getVisibleWeb(),
                s.getDescripcion(),
                veCosto ? s.getCosto() : null,
                veCosto ? s.getMargen() : null,
                s.getPrecioReferencial(),
                veCosto ? s.getPrecioManual() : null,
                s.getEstadoAprobacion(),
                s.getStatus(),
                s.getUserCreate(),
                s.getDateCreate(),
                s.getUserUpdate(),
                s.getDateUpdate());
    }
}