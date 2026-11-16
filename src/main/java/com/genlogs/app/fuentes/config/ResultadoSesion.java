package com.genlogs.app.fuentes.config;

import java.time.LocalDateTime;

/**
 * Lo que devuelve el servicio de lectura al intentar iniciar sesión en una tienda (L1).
 * La sesión (cookies) es secreta: nunca sale en toString ni en una respuesta HTTP.
 */
public record ResultadoSesion(Resultado resultado, String sesion, LocalDateTime validaHasta, String mensaje) {

    public enum Resultado { OK, VERIFICACION, ERROR }

    public static ResultadoSesion ok(String sesion, LocalDateTime validaHasta) {
        return new ResultadoSesion(Resultado.OK, sesion, validaHasta, null);
    }

    /** La tienda pidió un código o CAPTCHA que debe completar el Jefe Administrativo. */
    public static ResultadoSesion verificacion(String mensaje) {
        return new ResultadoSesion(Resultado.VERIFICACION, null, null, mensaje);
    }

    public static ResultadoSesion error(String mensaje) {
        return new ResultadoSesion(Resultado.ERROR, null, null, mensaje);
    }

    @Override
    public String toString() {
        return "ResultadoSesion[resultado=" + resultado + ", validaHasta=" + validaHasta + ", mensaje=" + mensaje + "]";
    }
}
