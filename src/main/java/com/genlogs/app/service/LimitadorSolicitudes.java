package com.genlogs.app.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.genlogs.app.exception.DemasiadasSolicitudesException;

/**
 * Tope en memoria de solicitudes por usuario: 10 cada 15 minutos. Frena un
 * doble clic repetido o un script, no a un cliente real. Por usuario y no por IP,
 * como LimitadorRegistroPortal (en Render todo puede salir del mismo proxy).
 * Al reiniciar la app el contador vuelve a cero; es aceptable para este uso.
 */
@Component
public class LimitadorSolicitudes {

    static final int MAXIMO = 10;
    private static final long VENTANA_MS = 15L * 60L * 1000L;

    private final ConcurrentHashMap<Long, Deque<Long>> envios = new ConcurrentHashMap<>();

    public void verificar(Long idUsuario) {
        long ahora = System.currentTimeMillis();
        Deque<Long> marcas = envios.computeIfAbsent(idUsuario, k -> new ArrayDeque<>());
        synchronized (marcas) {
            while (!marcas.isEmpty() && ahora - marcas.peekFirst() > VENTANA_MS) {
                marcas.removeFirst();
            }
            if (marcas.size() >= MAXIMO) {
                throw new DemasiadasSolicitudesException(
                        "Enviaste muchas solicitudes seguidas. Espera unos minutos e inténtalo de nuevo.");
            }
            marcas.addLast(ahora);
        }
    }
}
