package com.genlogs.app.vitrina.dto;

/** Categoría con cuántos repuestos publicados tiene (landing, RF-82). */
public record CategoriaConteoResponse(Integer idCategoria, String nombre, long cantidad) {
}
