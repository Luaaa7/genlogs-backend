package com.genlogs.app.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Respuesta paginada estable para el frontend: { content, totalElements, totalPages, page, size }. */
@Getter
@AllArgsConstructor
public class PaginaResponse<T> {
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;

    public static <T> PaginaResponse<T> of(Page<T> p) {
        return new PaginaResponse<>(p.getContent(), p.getTotalElements(), p.getTotalPages(), p.getNumber(), p.getSize());
    }
}
