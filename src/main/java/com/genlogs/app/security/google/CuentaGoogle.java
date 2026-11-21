package com.genlogs.app.security.google;

/**
 * Datos de la cuenta de Google ya verificados: el identificador estable de Google (sub),
 * el correo verificado (en minúsculas) y el nombre que muestra Google (puede venir vacío).
 */
public record CuentaGoogle(String sub, String correo, String nombre) {
}
