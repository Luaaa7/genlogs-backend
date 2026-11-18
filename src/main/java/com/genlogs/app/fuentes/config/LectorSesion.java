package com.genlogs.app.fuentes.config;

/**
 * L1 · Puerta hacia el servicio de lectura para iniciar sesión en una tienda con la cuenta de
 * búsqueda (D-38). La implementación es {@code LectorSesionRemoto}, que usa el cliente del servicio
 * de lectura (P2, Persona 6) y las reglas de la página LOGIN.
 */
public interface LectorSesion {

    ResultadoSesion iniciarSesion(CodigoFuente codigo, String urlBase, String usuario, String clave);
}
