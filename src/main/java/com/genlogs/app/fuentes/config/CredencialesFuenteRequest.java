package com.genlogs.app.fuentes.config;

/** Usuario y/o clave de una tienda (solo escritura). Lo que viene vacío no se cambia. */
public record CredencialesFuenteRequest(String usuario, String clave) {
}
