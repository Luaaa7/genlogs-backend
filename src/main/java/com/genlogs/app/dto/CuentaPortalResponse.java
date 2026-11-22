package com.genlogs.app.dto;

/** Lo que el portal necesita saber de la cuenta al recargar: si ya tiene empresa. */
public record CuentaPortalResponse(String nombre, String correo, boolean perfilCompleto, String empresa) {
}
