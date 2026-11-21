package com.genlogs.app.security.google;

/** Valida el token que entrega el botón «Continuar con Google» (ID token). */
public interface VerificadorTokenGoogle {

    /**
     * Devuelve la cuenta si el token es de Google, para esta aplicación, vigente y con
     * correo verificado. Si no, lanza BusinessException con un mensaje para el cliente.
     */
    CuentaGoogle verificar(String idToken);
}
