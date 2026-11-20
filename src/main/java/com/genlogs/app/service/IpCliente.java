package com.genlogs.app.service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP del cliente detrás del proxy de Render. Se usa el primer valor de
 * X-Forwarded-For, que es el que Render deja como IP de origen. Si ese
 * valor no tiene forma de IP, se ignora y se usa la dirección de la
 * conexión: así un cliente no mete texto arbitrario en el límite por IP.
 */
public final class IpCliente {

    private static final String IP = "^(?:\\d{1,3}(?:\\.\\d{1,3}){3}|[0-9a-fA-F:]{2,45})$";

    private IpCliente() {
    }

    public static String de(HttpServletRequest request) {
        if (request == null) {
            return "desconocida";
        }
        String encabezado = request.getHeader("X-Forwarded-For");
        if (encabezado != null && !encabezado.isBlank()) {
            String primero = encabezado.split(",")[0].trim();
            if (primero.matches(IP)) {
                return primero;
            }
        }
        String remota = request.getRemoteAddr();
        return remota == null || remota.isBlank() ? "desconocida" : remota;
    }
}
