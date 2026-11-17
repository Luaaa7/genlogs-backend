package com.genlogs.app.sourcing;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.genlogs.app.fuentes.config.FichaFuente;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizadorFichaTest {

    private final ConsultaBusqueda consulta = new ConsultaBusqueda(
            MetodoBusqueda.NUMERO_PARTE, "1R-0750", "CAT", null, null, null);

    @Test
    void precioVacioOCero_quedaNulo() {
        List<ResultadoBusqueda> r = NormalizadorFicha.normalizar(List.of(
                new FichaFuente("A", "d", "", "USD", null, "https://e/1"),
                new FichaFuente("B", "d", "0", "usd", null, "https://e/2"),
                new FichaFuente("C", "d", "18,50", "usd", null, "https://e/3")), consulta);

        assertThat(r.get(0).precio()).isNull();
        assertThat(r.get(1).precio()).isNull();
        assertThat(r.get(2).precio()).isEqualByComparingTo("18.50");
        assertThat(r.get(2).codigoMoneda()).isEqualTo("USD");
        assertThat(r.get(0).descripcion()).isEqualTo("d");
        assertThat(r.get(0).marca()).isEqualTo("CAT");
        assertThat(r.get(0).numeroParte()).isEqualTo("1R-0750");
    }

    @Test
    void fichaSinNombreNiEnlace_seDescarta() {
        List<ResultadoBusqueda> r = NormalizadorFicha.normalizar(List.of(
                new FichaFuente(" ", null, "1", "USD", null, " "),
                new FichaFuente("Filtro", null, "1", "USD", null, "https://e/1")), consulta);
        assertThat(r).hasSize(1);
        assertThat(r.get(0).titulo()).isEqualTo("Filtro");
    }

    @Test
    void unPatrocinadoQueSoloCambiaElRastreo_noSeRepite() {
        List<ResultadoBusqueda> r = NormalizadorFicha.normalizar(List.of(
                new FichaFuente("Filtro", null, "10", "USD", null,
                        "https://www.amazon.com/dp/B000123?ref=sr_1&qid=111"),
                new FichaFuente("Filtro patrocinado", null, "10", "USD", null,
                        "https://www.amazon.com/dp/B000123?ref=sspa&utm_source=x#x"),
                new FichaFuente("Filtro sin www", null, "10", "USD", null,
                        "https://amazon.com/dp/B000123?pd_rd_w=abc&pf_rd_p=def"),
                new FichaFuente("Otro", null, "11", "USD", null, "https://www.amazon.com/dp/B000999")), consulta);

        assertThat(r).extracting(ResultadoBusqueda::titulo).containsExactly("Filtro", "Otro");
        // Se guarda el enlace original de la primera, no la clave.
        assertThat(r.get(0).urlReferencia()).isEqualTo("https://www.amazon.com/dp/B000123?ref=sr_1&qid=111");
    }

    @Test
    void dosProductosConDistintoPdRdI_sonDosFichas() {
        List<ResultadoBusqueda> r = NormalizadorFicha.normalizar(List.of(
                new FichaFuente("Filtro A", null, "10", "USD", null,
                        "https://www.amazon.com/sspa/click?pd_rd_i=B0AAAA1111&ref=sspa&qid=1"),
                new FichaFuente("Filtro B", null, "12", "USD", null,
                        "https://www.amazon.com/sspa/click?pd_rd_i=B0BBBB2222&ref=sspa&qid=1"),
                new FichaFuente("Filtro A otra vez", null, "10", "USD", null,
                        "https://www.amazon.com/sspa/click?qid=2&pd_rd_i=B0AAAA1111")), consulta);

        assertThat(r).extracting(ResultadoBusqueda::titulo).containsExactly("Filtro A", "Filtro B");
    }

    @Test
    void laClaveDelEnlaceSoloQuitaElRastreo() {
        assertThat(NormalizadorFicha.claveEnlace("https://www.amazon.com/dp/B000123?ref=sr_1&utm_medium=x#reviews"))
                .isEqualTo("amazon.com/dp/B000123");
        assertThat(NormalizadorFicha.claveEnlace(
                "https://www.amazon.com/sspa/click?url=%2Fdp%2FB0AAAA1111&pd_rd_i=B0AAAA1111&sp_csd=x"))
                .isEqualTo("amazon.com/sspa/click?pd_rd_i=B0AAAA1111&url=%2Fdp%2FB0AAAA1111");
        assertThat(NormalizadorFicha.claveEnlace("https://www.ebay.com/itm/375375806750?_skw=CAT&itmmeta=01M&hash=item57"))
                .isEqualTo("ebay.com/itm/375375806750");
        assertThat(NormalizadorFicha.claveEnlace("https://www.aliexpress.com/item/100500.html?spm=a2g0o&algo_pvid=9"))
                .isEqualTo("aliexpress.com/item/100500.html");
        // Un enlace que no es URL válida se compara tal cual.
        assertThat(NormalizadorFicha.claveEnlace("no es url")).isEqualTo("no es url");
    }

    @Test
    void elRastreoDentroDeUrl_noCuentaComoOtroProducto() {
        List<ResultadoBusqueda> r = NormalizadorFicha.normalizar(List.of(
                new FichaFuente("Filtro", null, "10", "USD", null,
                        "https://www.amazon.com/sspa/click?pd_rd_i=B0AAAA1111&url=%2Fdp%2FB0AAAA1111%2Fref%3Dsspa_dk_detail_0%3Fpsc%3D1"),
                new FichaFuente("Filtro patrocinado", null, "10", "USD", null,
                        "https://www.amazon.com/sspa/click?pd_rd_i=B0AAAA1111&url=%2Fdp%2FB0AAAA1111%2Fref%3Dsspa_dk_detail_1%3Fpsc%3D1"),
                new FichaFuente("Otro", null, "11", "USD", null,
                        "https://www.amazon.com/sspa/click?pd_rd_i=B0BBBB2222&url=%2Fdp%2FB0BBBB2222%2Fref%3Dsspa_dk_detail_0")), consulta);

        assertThat(r).extracting(ResultadoBusqueda::titulo).containsExactly("Filtro", "Otro");
    }

    @Test
    void unParametroDeRastreoDeOtroSitio_noSeQuitaEnUnSitioDesconocido() {
        // "tag" es rastreo en Amazon, pero en otro sitio puede identificar el producto.
        assertThat(NormalizadorFicha.claveEnlace("https://www.tienda.com/p?id=1&tag=a"))
                .isEqualTo("tienda.com/p?id=1&tag=a");
        assertThat(NormalizadorFicha.claveEnlace("https://www.tienda.com/p?id=1&tag=b"))
                .isNotEqualTo(NormalizadorFicha.claveEnlace("https://www.tienda.com/p?id=1&tag=a"));
        assertThat(NormalizadorFicha.claveEnlace("https://www.tienda.com/p?id=1&utm_source=x&gclid=y"))
                .isEqualTo("tienda.com/p?id=1");
    }

    @Test
    void laBarraFinalYElRefDeLaRuta_noCambianLaClave() {
        assertThat(NormalizadorFicha.claveEnlace("https://www.amazon.com/dp/B000123/"))
                .isEqualTo(NormalizadorFicha.claveEnlace("https://www.amazon.com/dp/B000123/ref=sr_1_1"))
                .isEqualTo("amazon.com/dp/B000123");
    }
}
