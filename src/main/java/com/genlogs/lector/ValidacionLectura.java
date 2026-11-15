package com.genlogs.lector;

import com.fasterxml.jackson.databind.JsonNode;
import com.genlogs.app.sourcing.UrlSegura;
import com.genlogs.app.sourcing.lector.ContratoLector.*;
import com.genlogs.app.sourcing.lector.LectorRemotoException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validación sin red; los destinos DNS se comprueban en el proxy al conectar. */
final class ValidacionLectura {
    private ValidacionLectura() { }
    static final Map<String, List<String>> DOMINIOS = Map.of(
            "EBAY", List.of("ebay.com"), "AMAZON", List.of("amazon.com"),
            "ALIEXPRESS", List.of("aliexpress.com"), "ALIBABA", List.of("alibaba.com"),
            "MERCADO_LIBRE", List.of("mercadolibre.com.pe", "mercadolibre.com", "mercadolibre.com.mx", "mercadolibre.com.ar"));
    private static final Set<String> CAMPOS = Set.of("nombre", "descripcion", "precio", "moneda", "imagen", "enlace");
    private static final Set<String> ATRIBUTOS = Set.of("content", "href", "src", "data-src");

    static boolean dominio(String host, String fuente) {
        return DOMINIOS.getOrDefault(fuente, List.of()).stream().anyMatch(d -> host.equals(d) || host.endsWith("." + d));
    }

    static URI url(String value, String fuente) {
        try {
            URI uri = UrlSegura.validarFormato(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !dominio(uri.getHost().toLowerCase(java.util.Locale.ROOT), fuente)) fallar(Estado.URL_NO_PERMITIDA);
            return uri;
        } catch (LectorRemotoException e) { throw e; }
        catch (RuntimeException e) { throw new LectorRemotoException(Estado.URL_NO_PERMITIDA); }
    }

    static void pedido(String operacion, Peticion req) {
        if (req == null || req.fuente() == null || !DOMINIOS.containsKey(req.fuente()) || req.reglas() == null
                || req.url() == null || req.url().length() > 4096) fallar(Estado.SOLICITUD_INVALIDA);
        url(req.url(), req.fuente());
        if (req.maxResultados() != null && (req.maxResultados() < 1 || req.maxResultados() > 50)) fallar(Estado.SOLICITUD_INVALIDA);
        Map<String, Object> rules = req.reglas();
        for (var entry : rules.entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
            if (CAMPOS.contains(key)) {
                if (!(value instanceof Map<?, ?>)) fallar(Estado.SOLICITUD_INVALIDA);
                Map<?, ?> field = (Map<?, ?>) value;
                selector(field.get("selector"));
                if (field.keySet().stream().anyMatch(k -> !Set.of("selector", "atributo").contains(k))
                        || (field.containsKey("atributo") && !ATRIBUTOS.contains(field.get("atributo")))) fallar(Estado.SOLICITUD_INVALIDA);
            } else if (Set.of("contenedor", "listo", "sinResultados", "verificacion", "bloqueo", "sesionVencida").contains(key)) {
                selector(value);
            } else if ("codigoMoneda".equals(key)) {
                if (!(value instanceof String s) || !s.matches("[A-Z]{3}")) fallar(Estado.SOLICITUD_INVALIDA);
            } else if ("separadorDecimal".equals(key)) {
                if (!Set.of(".", ",").contains(value)) fallar(Estado.SOLICITUD_INVALIDA);
            } else if ("login".equals(key)) {
                if (!(value instanceof Map<?, ?> login) || !login.keySet().equals(Set.of("usuario", "clave", "enviar", "exito"))) {
                    fallar(Estado.SOLICITUD_INVALIDA);
                }
                ((Map<?, ?>) value).values().forEach(ValidacionLectura::selector);
            } else fallar(Estado.SOLICITUD_INVALIDA);
        }
        if ("sesion".equals(operacion)) {
            Credenciales c = req.credenciales();
            if (c == null || c.usuario() == null || c.usuario().isBlank() || c.usuario().length() > 256
                    || c.clave() == null || c.clave().isEmpty() || c.clave().length() > 1024 || !rules.containsKey("login")) fallar(Estado.SOLICITUD_INVALIDA);
        } else if (req.credenciales() != null || !rules.containsKey("nombre") || !rules.containsKey("precio")
                || ("buscar".equals(operacion) && (!rules.containsKey("contenedor") || !rules.containsKey("enlace")))) {
            fallar(Estado.SOLICITUD_INVALIDA);
        }
        if (req.sesion() != null) validarSesion(req.sesion(), req.fuente());
    }

    static void validarSesion(Sesion sesion, String fuente) {
        try {
            if (!Instant.parse(sesion.validaHasta()).isAfter(Instant.now())) fallar(Estado.SESION_VENCIDA);
            JsonNode state = sesion.storageState();
            if (state == null || !state.path("cookies").isArray() || !state.path("origins").isArray()
                    || state.path("cookies").size() > 200 || state.path("origins").size() > 20) fallar(Estado.SOLICITUD_INVALIDA);
            for (JsonNode cookie : state.path("cookies")) {
                if (!dominio(cookie.path("domain").asText().replaceFirst("^\\.", ""), fuente)) fallar(Estado.SOLICITUD_INVALIDA);
            }
            for (JsonNode origin : state.path("origins")) url(origin.path("origin").asText(), fuente);
        } catch (LectorRemotoException e) { throw e; }
        catch (RuntimeException e) { throw new LectorRemotoException(Estado.SOLICITUD_INVALIDA); }
    }

    private static void selector(Object value) {
        if (!(value instanceof String s) || s.isBlank() || s.length() > 500) fallar(Estado.SOLICITUD_INVALIDA);
    }
    static void fallar(Estado estado) { throw new LectorRemotoException(estado); }
}
