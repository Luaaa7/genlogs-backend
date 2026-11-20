package com.genlogs.app.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.genlogs.app.exception.DemasiadasSolicitudesException;

/** Tope de la consulta pública de RUC: 10 por IP cada 15 minutos. */
@Component
public class LimitadorConsultaRuc {

    static final int MAXIMO = 10;
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
                        "Demasiadas consultas de RUC. Espera 15 minutos.");
            }
            marcas.addLast(ahora);
        }
    }
}
