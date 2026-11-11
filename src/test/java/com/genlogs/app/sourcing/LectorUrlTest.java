package com.genlogs.app.sourcing;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LectorUrlTest {

    private final LectorUrl lector = new LectorUrl();

    private LectorUrl.DatosUrl extraer(String html) {
        return lector.extraer(Jsoup.parse(html, "https://www.tienda.com/p/1"), "https://www.tienda.com/p/1", "PROVEEDOR");
    }

    @Test
    void leeMetadatosOpenGraph() {
        LectorUrl.DatosUrl d = extraer("""
                <html><head>
                  <meta property="og:title" content="Filtro de aceite CAT 1R-0750">
                  <meta property="og:image" content="https://img.tienda.com/1r0750.jpg">
                  <meta property="product:price:amount" content="18.50">
                  <meta property="product:price:currency" content="usd">
                </head><body></body></html>""");

        assertThat(d.titulo()).isEqualTo("Filtro de aceite CAT 1R-0750");
        assertThat(d.urlImagen()).isEqualTo("https://img.tienda.com/1r0750.jpg");
        assertThat(d.precio()).isEqualByComparingTo("18.50");
        assertThat(d.codigoMoneda()).isEqualTo("USD");
        assertThat(d.bloqueada()).isFalse();
    }

    @Test
    void usaJsonLdCuandoNoHayMetadatos() {
        LectorUrl.DatosUrl d = extraer("""
                <html><head><title>Rodamiento SKF 6205</title>
                <script type="application/ld+json">
                {"@type":"Product","name":"Rodamiento","image":["https://img.tienda.com/6205.jpg"],
                 "offers":{"@type":"Offer","price":"1,234.56","priceCurrency":"USD"}}
                </script></head><body></body></html>""");

        assertThat(d.titulo()).isEqualTo("Rodamiento SKF 6205");
        assertThat(d.precio()).isEqualByComparingTo("1234.56");
        assertThat(d.codigoMoneda()).isEqualTo("USD");
        assertThat(d.urlImagen()).isEqualTo("https://img.tienda.com/6205.jpg");
    }

    @Test
    void usaMicrodatosItemprop() {
        LectorUrl.DatosUrl d = extraer("""
                <html><body><h1>Manguera</h1>
                  <span itemprop="price" content="250.00">S/ 250</span>
                  <meta itemprop="priceCurrency" content="PEN">
                </body></html>""");

        assertThat(d.precio()).isEqualByComparingTo("250.00");
        assertThat(d.codigoMoneda()).isEqualTo("PEN");
    }

    @Test
    void sinPrecioNiImagen_quedaBloqueadaParaLlenarAMano() {
        LectorUrl.DatosUrl d = extraer("<html><head><title>Robot check</title></head><body></body></html>");

        assertThat(d.bloqueada()).isTrue();
        assertThat(d.motivo()).contains("a mano");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "18.50|18.50",
            "18,50|18.50",
            "1,234.56|1234.56",
            "1.234,56|1234.56",
            "US$ 25|25",
            "S/ 1 250.90|1250.90"
    })
    void normalizaPrecios(String texto, String esperado) {
        assertThat(LectorUrl.normalizarPrecio(texto)).isEqualByComparingTo(esperado);
    }

    @Test
    void precioVacioOCero_esNull() {
        assertThat(LectorUrl.normalizarPrecio("Consultar")).isNull();
        assertThat(LectorUrl.normalizarPrecio("0.00")).isNull();
        assertThat(LectorUrl.normalizarPrecio(null)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "www.amazon.com, AMAZON",
            "spanish.alibaba.com, ALIBABA",
            "www.ebay.com, EBAY",
            "articulo.mercadolibre.com.pe, MERCADO_LIBRE",
            "www.ferreyros.com.pe, PROVEEDOR"
    })
    void detectaLaFuentePorElSitio(String host, String fuente) {
        assertThat(LectorUrl.detectarFuente(host)).isEqualTo(fuente);
    }

    @Test
    void descartaImagenesQueNoSonHttp() {
        LectorUrl.DatosUrl d = extraer("""
                <html><head>
                  <meta property="og:image" content="javascript:alert(1)">
                  <meta property="product:price:amount" content="10">
                </head><body></body></html>""");

        assertThat(d.urlImagen()).isNull();
        assertThat(d.precio()).isEqualByComparingTo("10");
    }
}
