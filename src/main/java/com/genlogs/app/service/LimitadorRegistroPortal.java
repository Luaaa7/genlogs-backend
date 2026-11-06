package com.genlogs.app.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.genlogs.app.exception.DemasiadasSolicitudesException;

/**
 * Tope en memoria por documento y por correo. No usa la IP: en Render todas
 * las peticiones pueden salir de un mismo proxy y un límite por IP bloquearía
 * a clientes distintos.
 */
@Component
public class LimitadorRegistroPortal {

    private static final int MAXIMO = 5;
    private static final long VENTANA_MS = 15L * 60L * 1000L;

    private final ConcurrentHashMap<String, Deque<Long>> intentos = new ConcurrentHashMap<>();

    public void verificar(String documento, String correo) {
        registrar("doc:" + documento);
        registrar("correo:" + correo.toLowerCase(Locale.ROOT));
    }

    private void registrar(String clave) {
        long ahora = System.currentTimeMillis();
        Deque<Long> marcas = intentos.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (marcas) {
            while (!marcas.isEmpty() && ahora - marcas.peekFirst() > VENTANA_MS) {
                marcas.removeFirst();
            }
            if (marcas.size() >= MAXIMO) {
                throw new DemasiadasSolicitudesException();
            }
            marcas.addLast(ahora);
        }
    }
}
