package com.genlogs.lector;

import com.genlogs.app.sourcing.lector.ContratoLector.Peticion;
import java.util.List;
import java.util.Map;

/** P4: diez HTML sintéticos guardados en Java, sin cuentas ni acceso a tiendas reales. */
final class PaginasPrueba {
    record Caso(String fuente, String host, String contenedor, String titulo, String precio,
                String atributoImagen, String importe, String esperado, String moneda, String separador) {
        Map<String, Object> reglas() {
            return Map.of("contenedor", contenedor, "nombre", Map.of("selector", titulo),
                    "descripcion", Map.of("selector", ".description"), "precio", Map.of("selector", precio),
                    "imagen", Map.of("selector", "img", "atributo", atributoImagen),
                    "enlace", Map.of("selector", "a", "atributo", "href"), "codigoMoneda", moneda,
                    "separadorDecimal", separador, "sinResultados", "#empty");
        }
        Peticion peticion(boolean buscar) {
            return new Peticion(fuente, "https://" + host + (buscar ? "/buscar" : "/producto/1"), reglas(), null, null, 8);
        }
        String ficha() {
            String container = contenedor.startsWith(".") ? "class=\"" + contenedor.substring(1) + "\"" : "data-component-type=\"s-search-result\"";
            return """
                    <!doctype html><html lang="es"><head><meta charset="utf-8"><title>Fixture sintético</title></head><body>
                    <article %s><h2 class="%s">Filtro CAT 1R-0750</h2>
                    <p class="description">Filtro de combustible de prueba</p>
                    <span class="%s">%s</span><a href="/producto/1">Ver ficha</a>
                    <img %s="/foto.jpg" alt="Filtro de prueba"></article></body></html>
                    """.formatted(container, titulo.substring(1), precio.substring(1), importe, atributoImagen);
        }
        String busqueda() { return ficha().replace("</body>", ficha().substring(ficha().indexOf("<article"), ficha().indexOf("</body>")).replace("/producto/1", "/producto/2") + "</body>"); }
    }
    static final List<Caso> CASOS = List.of(
            new Caso("MERCADO_LIBRE", "www.mercadolibre.com.pe", ".ui-search-layout__item", ".poly-component__title", ".andes-money-amount", "src", "S/ 125.50", "125.50", "PEN", "."),
            new Caso("EBAY", "www.ebay.com", ".s-item", ".s-item__title", ".s-item__price", "src", "US $25.99", "25.99", "USD", "."),
            new Caso("AMAZON", "www.amazon.com", "[data-component-type=s-search-result]", ".title", ".a-offscreen", "src", "$1,234.56", "1234.56", "USD", "."),
            new Caso("ALIEXPRESS", "www.aliexpress.com", ".search-item", ".item-title", ".price", "data-src", "US $18,50", "18.50", "USD", ","),
            new Caso("ALIBABA", "www.alibaba.com", ".organic-list-offer", ".title", ".price", "src", "US $40.00", "40.00", "USD", ".")
    );
}
