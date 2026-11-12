package com.genlogs.app.fuentes;

import java.net.http.HttpRequest;

/**
 * Llamada HTTP de las fuentes externas. Existe para que las pruebas simulen eBay sin
 * red: la implementación real es {@link ClienteHttpJdk}.
 */
public interface ClienteHttp {

    /**
     * Envía la petición y devuelve estado y cuerpo, sea cual sea el código HTTP.
     *
     * @throws FuenteNoDisponibleException si no hubo respuesta (red, tiempo agotado)
     */
    RespuestaHttp enviar(HttpRequest peticion);

    /** Estado HTTP y cuerpo de texto de una respuesta. */
    record RespuestaHttp(int estado, String cuerpo) {

        public boolean exitosa() {
            return estado >= 200 && estado < 300;
        }
    }
}
