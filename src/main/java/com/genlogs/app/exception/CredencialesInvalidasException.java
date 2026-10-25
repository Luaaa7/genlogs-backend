package com.genlogs.app.exception;

/**
 * Usuario o contraseña incorrectos en el login. Se responde con 401
 * (no autenticado) en lugar de 400. Hereda de BusinessException para que
 * cualquier código que ya la capture como BusinessException siga funcionando.
 */
public class CredencialesInvalidasException extends BusinessException {

    public CredencialesInvalidasException() {
        super("Usuario o contraseña incorrectos");
    }
}