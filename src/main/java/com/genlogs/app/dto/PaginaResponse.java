package com.genlogs.app.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Respuesta paginada estable para el frontend:
 * { content, totalElements, totalPages, page, size, currentPage, pageSize }.
 *
 * "currentPage" y "pageSize" son alias de "page" y "size": el frontend los
 * declara en sus tipos (CotizacionesListResponse, common.types) y los usa
 * como índice de página base 0.
 */
@Getter
@AllArgsConstructor
public class PaginaResponse<T> {
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
    /** Alias de page. */
    private int currentPage;
    /** Alias de size. */
    private int pageSize;

    public static <T> PaginaResponse<T> of(Page<T> p) {
        return new PaginaResponse<>(p.getContent(), p.getTotalElements(), p.getTotalPages(),
                p.getNumber(), p.getSize(), p.getNumber(), p.getSize());
    }
}
