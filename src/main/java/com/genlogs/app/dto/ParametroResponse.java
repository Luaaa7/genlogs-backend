package com.genlogs.app.dto;

import java.time.LocalDateTime;

/** Lo que lee el frontend en /parametros: clave, valor, descripcion, editadoEn. */
public record ParametroResponse(String clave, String valor, String descripcion, LocalDateTime editadoEn) {
}
