package com.genlogs.app.fuentes.config;

/** Respuesta por defecto mientras el servicio de lectura (P2) no está conectado al backend. */
public class LectorSesionNoConectado implements LectorSesion {

    static final String MENSAJE = "El servicio de lectura todavía no está conectado; no se pudo probar la sesión.";

    @Override
    public ResultadoSesion iniciarSesion(CodigoFuente codigo, String urlBase, String usuario, String clave) {
        return ResultadoSesion.error(MENSAJE);
    }
}
