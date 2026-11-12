package com.genlogs.app.fuentes;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Component;

/** {@link ClienteHttp} con el HttpClient del JDK, igual que FactilizaService. */
@Component
public class ClienteHttpJdk implements ClienteHttp {

    private final HttpClient cliente = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public RespuestaHttp enviar(HttpRequest peticion) {
        try {
            HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
            return new RespuestaHttp(respuesta.statusCode(), respuesta.body());
        } catch (IOException e) {
            throw new FuenteNoDisponibleException("sin respuesta de " + peticion.uri().getHost() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FuenteNoDisponibleException("consulta interrumpida a " + peticion.uri().getHost());
        }
    }
}
