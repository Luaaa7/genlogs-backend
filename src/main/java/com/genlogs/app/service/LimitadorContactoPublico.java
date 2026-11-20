package com.genlogs.app.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.genlogs.app.exception.DemasiadasSolicitudesException;

/** Tope del formulario de contacto: 5 envíos por IP cada 15 minutos. */
@Component
public class LimitadorContactoPublico {

    static final int MAXIMO = 5;
    private static final long VENTANA_MS = 15L * 60L * 1000L;

    private final ConcurrentHashMap<String, Deque<Long>> intentos = new ConcurrentHashMap<>();

    public void verificar(String ip) {
        long ahora = System.currentTimeMillis();
        Deque<Long> marcas = intentos.computeIfAbsent(ip == null ? "desconocida" : ip, k -> new ArrayDeque<>());
        synchronized (marcas) {
            while (!marcas.isEmpty() && ahora - marcas.peekFirst() > VENTANA_MS) {
                marcas.removeFirst();
            }
            if (marcas.size() >= MAXIMO) {
                throw new DemasiadasSolicitudesException(
                        "Demasiados mensajes. Espera 15 minutos.");
            }
            marcas.addLast(ahora);
        }
    }
}
