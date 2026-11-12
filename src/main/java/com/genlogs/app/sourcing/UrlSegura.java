package com.genlogs.app.sourcing;

import com.genlogs.app.exception.BusinessException;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Reglas para leer o guardar URLs de terceros (una sola clase para todo el backend).
 * Evita que el servidor se use para llegar a su red interna o a la metadata de la nube.
 *
 * - validarFormato: solo http/https, con sitio y en el puerto 80/443 (o sin puerto). No consulta la red.
 * - validarDestino: el sitio no puede resolver a una dirección interna (IPv4 ni IPv6).
 *
 * Riesgo conocido (no bloqueante): se resuelve el DNS para validar y el cliente HTTP lo vuelve a
 * resolver al conectar (DNS rebinding). Lo mitiga que solo lo usa el Asistente Logístico y que
 * está detrás del interruptor genlogs.busqueda.habilitada.
 */
public final class UrlSegura {

    private UrlSegura() {
    }

    public static URI validarFormato(String url) {
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("La URL no es válida");
        }
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean http = esquema.equals("http") || esquema.equals("https");
        if (!http || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessException("La URL debe empezar con http:// o https://");
        }
        if (uri.getUserInfo() != null) {
            throw new BusinessException("La URL no puede llevar usuario ni contraseña");
        }
        int puerto = uri.getPort();
        if (puerto != -1 && puerto != 80 && puerto != 443) {
            throw new BusinessException("Solo se aceptan URLs en el puerto 80 o 443");
        }
        return uri;
    }

    public static void validarDestino(URI uri) {
        InetAddress[] ips;
        try {
            ips = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new BusinessException("No se encontró el sitio " + uri.getHost());
        }
        for (InetAddress ip : ips) {
            if (esInterna(ip)) {
                throw new BusinessException("No se pueden leer direcciones internas");
            }
        }
    }

    /** true si la dirección no es pública: loopback, privada, enlace local, CGNAT, ULA, etc. */
    static boolean esInterna(InetAddress ip) {
        if (ip.isLoopbackAddress() || ip.isSiteLocalAddress() || ip.isLinkLocalAddress()
                || ip.isAnyLocalAddress() || ip.isMulticastAddress()) {
            return true;
        }
        byte[] b = ip.getAddress();
        if (ip instanceof Inet4Address) {
            int o1 = b[0] & 0xFF, o2 = b[1] & 0xFF;
            return o1 == 0                                   // 0.0.0.0/8
                    || (o1 == 100 && o2 >= 64 && o2 <= 127)  // 100.64.0.0/10 (CGNAT)
                    || (o1 == 192 && o2 == 0 && (b[2] & 0xFF) == 0) // 192.0.0.0/24
                    || (o1 == 198 && (o2 == 18 || o2 == 19)) // 198.18.0.0/15
                    || o1 >= 240;                            // 240.0.0.0/4 y 255.255.255.255
        }
        if (ip instanceof Inet6Address) {
            int o1 = b[0] & 0xFF;
            if ((o1 & 0xFE) == 0xFC) return true;            // fc00::/7 (ULA, p. ej. fd00::1)
            // IPv4 incrustada (::ffff:a.b.c.d o ::a.b.c.d): se revisa la IPv4
            boolean ceros = true;
            for (int i = 0; i < 10; i++) ceros &= b[i] == 0;
            boolean mapeada = ceros && ((b[10] == 0 && b[11] == 0) || (b[10] == (byte) 0xFF && b[11] == (byte) 0xFF));
            if (mapeada) {
                try {
                    return esInterna(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Para guardar una URL de imagen: misma regla de formato; null o vacío se aceptan. */
    public static String imagenOpcional(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            return validarFormato(url).toString();
        } catch (BusinessException e) {
            throw new BusinessException("La URL de la imagen debe empezar con http:// o https://");
        }
    }
}
