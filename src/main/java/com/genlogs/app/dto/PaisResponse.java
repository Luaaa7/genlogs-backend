package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaisResponse {
    private Integer idPais;
    private String codigoIso;
    private String nombrePais;
    /** Prefijo telefónico con "+" (p. ej. +51); null si el usuario debe escribirlo. */
    private String codigoTelefono;
}