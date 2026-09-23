package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaracteristicaTecnicaResponse {
    private Integer idCaracteristica;
    private String nombreCaracteristica;
    private String unidadCaracteristica;   // ej. "kg", "mm" (puede ser null)
}
