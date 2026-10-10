package com.genlogs.app.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Respuesta paginada con la forma que espera el frontend (content, totalElements, totalPages, page, size). */
@Getter
@AllArgsConstructor
public class PageResponseDto<T> {
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
}