package com.genlogs.app.fuentes;

import java.net.http.HttpRequest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Simula la plataforma: devuelve, en orden, las respuestas encoladas para cada ruta
 * (la que más se parezca, p. ej. "/oauth2/token" o "/search") y guarda las peticiones.
 */
class HttpSimulado implements ClienteHttp {

    private final List<Ruta> rutas = new ArrayList<>();
    final List<HttpRequest> peticiones = new ArrayList<>();

    HttpSimulado responder(String fragmentoRuta, int estado, String cuerpo) {
        Ruta ruta = rutas.stream().filter(r -> r.fragmento.equals(fragmentoRuta)).findFirst().orElseGet(() -> {
            Ruta nueva = new Ruta(fragmentoRuta);
            rutas.add(nueva);
            return nueva;
        });
        ruta.respuestas.add(new RespuestaHttp(estado, cuerpo));
        return this;
    }

    @Override
    public RespuestaHttp enviar(HttpRequest peticion) {
        peticiones.add(peticion);
        String uri = peticion.uri().toString();
        for (Ruta ruta : rutas) {
            if (uri.contains(ruta.fragmento)) {
                if (ruta.respuestas.size() > 1) {
                    return ruta.respuestas.poll();
                }
                return ruta.respuestas.peek();
            }
        }
        throw new AssertionError("Petición no esperada: " + uri);
    }

    long llamadasA(String fragmentoRuta) {
        return peticiones.stream().filter(p -> p.uri().toString().contains(fragmentoRuta)).count();
    }

    HttpRequest ultimaA(String fragmentoRuta) {
        HttpRequest ultima = null;
        for (HttpRequest p : peticiones) {
            if (p.uri().toString().contains(fragmentoRuta)) {
                ultima = p;
            }
        }
        return ultima;
    }

    private static final class Ruta {
        final String fragmento;
        final Deque<RespuestaHttp> respuestas = new ArrayDeque<>();

        Ruta(String fragmento) {
            this.fragmento = fragmento;
        }
    }
}
