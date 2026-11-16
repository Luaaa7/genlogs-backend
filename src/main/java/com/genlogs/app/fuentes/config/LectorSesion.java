package com.genlogs.app.fuentes.config;

/**
 * L1 · Puerta hacia el servicio de lectura para iniciar sesión en una tienda con la cuenta de
 * búsqueda (D-38). La implementación real usa el cliente del servicio de lectura (P2, Persona 6);
 * mientras no esté conectado se usa {@link LectorSesionNoConectado}.
 */
public interface LectorSesion {

    ResultadoSesion iniciarSesion(CodigoFuente codigo, String urlBase, String usuario, String clave);
}
