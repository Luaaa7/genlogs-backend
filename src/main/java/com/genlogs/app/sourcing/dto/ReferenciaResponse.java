package com.genlogs.app.sourcing.dto;

import com.genlogs.app.sourcing.EstadoSourcing;
import com.genlogs.app.sourcing.HistorialSourcing;
import com.genlogs.app.sourcing.MetodoBusqueda;
import com.genlogs.app.sourcing.OrigenMarcaUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Referencia para el panel interno. Trae fuente, enlace y precio original:
 * solo la reciben GERENTE_GENERAL, JEFE_ADMINISTRATIVO y ASISTENTE_LOGISTICO
 * (los @PreAuthorize del controlador). Nunca se usa en endpoints públicos.
 */
public record ReferenciaResponse(
        Long idSourcing,
        Long idSolicitudItem,
        String fuente,
        MetodoBusqueda metodo,
        String titulo,
        String marca,
        String numeroParte,
        BigDecimal precioRef,
        String codigoMoneda,
        BigDecimal costoPuestoLima,
        String urlReferencia,
        String urlImagenRef,
        EstadoSourcing estado,
        boolean editadoAMano,
        LocalDateTime creadoEn,
        String descripcion,
        OrigenMarcaUtil marcadaUtilDesde,
        LocalDateTime marcadaUtilEn,
        String marcadaUtilPor) {

    public static ReferenciaResponse de(HistorialSourcing h) {
        return de(h, null);
    }

    public static ReferenciaResponse de(HistorialSourcing h, String descripcion) {
        return new ReferenciaResponse(
                h.getIdSourcing(),
                h.getSolicitudItem() != null ? h.getSolicitudItem().getIdSolicitudItem() : null,
                h.getFuente(),
                h.getMetodo(),
                h.getTitulo(),
                h.getMarcaTexto(),
                h.getNumeroParte(),
                h.getPrecioRef(),
                h.getMoneda() != null ? h.getMoneda().getCodigoMoneda() : null,
                h.getCostoPuestoLima(),
                h.getUrlReferencia(),
                h.getUrlImagenRef(),
                h.getEstado(),
                Boolean.TRUE.equals(h.getEditadoAMano()),
                h.getCreadoEn(),
                descripcion,
                h.getMarcadaUtilDesde(),
                h.getMarcadaUtilEn(),
                h.getMarcadaUtilPor() != null ? h.getMarcadaUtilPor().getNombreUsuario() : null);
    }
}
