package com.genlogs.app.sourcing;

import java.util.Locale;
import java.util.Optional;

import com.genlogs.app.fuentes.config.CodigoFuente;

/** Relaciona el host de una URL con una tienda de fuente_busqueda, si aplica. */
final class HostFuente {

    private HostFuente() {
    }

    static Optional<CodigoFuente> de(String host) {
        String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (h.contains("amazon.")) {
            return Optional.of(CodigoFuente.AMAZON);
        }
        if (h.contains("ebay.")) {
            return Optional.of(CodigoFuente.EBAY);
        }
        if (h.contains("mercadolibre.") || h.contains("mercadolivre.")) {
            return Optional.of(CodigoFuente.MERCADO_LIBRE);
        }
        if (h.contains("aliexpress.")) {
            return Optional.of(CodigoFuente.ALIEXPRESS);
        }
        if (h.contains("alibaba.")) {
            return Optional.of(CodigoFuente.ALIBABA);
        }
        return Optional.empty();
    }
}
