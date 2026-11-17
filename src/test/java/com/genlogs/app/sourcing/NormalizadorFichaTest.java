package com.genlogs.app.sourcing;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.genlogs.app.fuentes.config.FichaFuente;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizadorFichaTest {

    private final ConsultaBusqueda consulta = new ConsultaBusqueda(
            MetodoBusqueda.NUMERO_PARTE, "1R-0750", "CAT", null, null, null);

    private static FichaFuente ficha(String nombre, String enlace) {
        return new FichaFuente(nombre, null, "10", "USD", null, enlace);
    }


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

    // ---------- normalizar (de tu rama, adaptado a FichaFuente) ----------

    @Test
    void normalizarListaVacia_devuelveVacia() {
        assertThat(NormalizadorFicha.normalizar(List.of(), consulta)).isEmpty();
        assertThat(NormalizadorFicha.normalizar(null, consulta)).isEmpty();
    }

    @Test
    void normalizarSinDuplicados_devuelveMismos() {
        List<ResultadoBusqueda> r = NormalizadorFicha.normalizar(List.of(
                ficha("A", "https://e.com/1"),
                ficha("B", "https://e.com/2")), consulta);
        assertThat(r).hasSize(2);
        assertThat(r).extracting(ResultadoBusqueda::titulo).containsExactly("A", "B");
    }

    @Test
    void normalizarConDuplicadosPorClave_soloPrimero() {
        List<ResultadoBusqueda> r = NormalizadorFicha.normalizar(List.of(
                ficha("A", "https://www.amazon.com/dp/B000123?ref=sr_1&qid=111"),
                ficha("A patrocinado", "https://www.amazon.com/dp/B000123?ref=sspa&utm_source=x#x"),
                ficha("B", "https://www.amazon.com/dp/B000999")), consulta);
        assertThat(r).hasSize(2);
        assertThat(r).extracting(ResultadoBusqueda::titulo).containsExactly("A", "B");
    }

    @Test
    void maximoOchoFichas() {
        List<FichaFuente> muchas = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            muchas.add(ficha("Item " + i, "https://e.com/" + i));
        }
        List<ResultadoBusqueda> r = NormalizadorFicha.normalizar(muchas, consulta);
        assertThat(r).hasSize(8);
    }

    // ---------- claveEnlace (de tu rama, sin cambios) ----------

    @Test
    void claveIncluyeProtocoloYHostNormalizado() {
        assertThat(NormalizadorFicha.claveEnlace("http://ejemplo.com/p"))
                .isEqualTo("http://ejemplo.com/p");
        assertThat(NormalizadorFicha.claveEnlace("https://ejemplo.com/p"))
                .isEqualTo("https://ejemplo.com/p");
        assertThat(NormalizadorFicha.claveEnlace("http://www.ejemplo.com/p"))
                .isEqualTo("http://ejemplo.com/p");
        assertThat(NormalizadorFicha.claveEnlace("https://www.ejemplo.com/p"))
                .isEqualTo("https://ejemplo.com/p");
        // El camino se conserva tal cual (case-sensitive)
        assertThat(NormalizadorFicha.claveEnlace("HTTP://EJEMPLO.COM/P"))
                .isEqualTo("http://ejemplo.com/P");
        assertThat(NormalizadorFicha.claveEnlace("HTTPS://WWW.EJEMPLO.COM/P"))
                .isEqualTo("https://ejemplo.com/P");
    }

    @Test
    void httpYHttpsNoSonLaMismaClave() {
        String claveHttp = NormalizadorFicha.claveEnlace("http://ejemplo.com/producto/123");
        String claveHttps = NormalizadorFicha.claveEnlace("https://ejemplo.com/producto/123");
        assertThat(claveHttp).startsWith("http://");
        assertThat(claveHttps).startsWith("https://");
        assertThat(claveHttp).isNotEqualTo(claveHttps);
    }

    @Test
    void puertosNoEstandardSeIncluyen() {
        assertThat(NormalizadorFicha.claveEnlace("http://ejemplo.com:80/producto"))
                .isEqualTo("http://ejemplo.com/producto");
        assertThat(NormalizadorFicha.claveEnlace("http://ejemplo.com:8080/producto"))
                .isEqualTo("http://ejemplo.com:8080/producto");
        assertThat(NormalizadorFicha.claveEnlace("https://ejemplo.com:443/producto"))
                .isEqualTo("https://ejemplo.com/producto");
        assertThat(NormalizadorFicha.claveEnlace("https://ejemplo.com:8443/producto"))
                .isEqualTo("https://ejemplo.com:8443/producto");
    }

    @Test
    void ordenDeParametrosNoAfectaDeduplicacion() {
        String clave1 = NormalizadorFicha.claveEnlace("https://ejemplo.com/p?color=rojo&size=M");
        String clave2 = NormalizadorFicha.claveEnlace("https://ejemplo.com/p?size=M&color=rojo");
        assertThat(clave1).isEqualTo(clave2);
    }

    @Test
    void parametrosRastreoSeEliminan() {
        String clave = NormalizadorFicha.claveEnlace(
                "https://amazon.com/dp/B001?ref=sr_1&qid=111&utm_source=google&pf_rd_r=abc&pd_rd_w=xyz");
        assertThat(clave).doesNotContain("ref=");
        assertThat(clave).doesNotContain("qid=");
        assertThat(clave).doesNotContain("utm_source=");
        assertThat(clave).doesNotContain("pf_rd_r=");
        assertThat(clave).doesNotContain("pd_rd_w=");
        assertThat(clave).isEqualTo("https://amazon.com/dp/B001");
    }

    @Test
    void thYKeywordsSonRastreoPeroSmidNo() {
        // th y keywords están en RASTREO
        String claveSinTh = NormalizadorFicha.claveEnlace("https://ejemplo.com/p?th=1&keywords=buscar");
        assertThat(claveSinTh).isEqualTo("https://ejemplo.com/p");

        // smid NO está en RASTREO (identifica vendedor)
        String claveConSmId = NormalizadorFicha.claveEnlace("https://ejemplo.com/p?smid=A123");
        assertThat(claveConSmId).contains("smid=A123");
    }

    @Test
    void pdRdISigueDiferenciandoProductos() {
        String clave1 = NormalizadorFicha.claveEnlace("https://amazon.com/sspa/click?pd_rd_i=B000111&ref=sspa");
        String clave2 = NormalizadorFicha.claveEnlace("https://amazon.com/sspa/click?pd_rd_i=B000222&ref=sspa");
        assertThat(clave1).isNotEqualTo(clave2);
        assertThat(clave1).contains("pd_rd_i=B000111");
        assertThat(clave2).contains("pd_rd_i=B000222");
    }

    @Test
    void parametrosVendedorNoSeEliminan() {
        String clave1 = NormalizadorFicha.claveEnlace("https://amazon.com/dp/B001?smid=A123&ref=sr");
        String clave2 = NormalizadorFicha.claveEnlace("https://amazon.com/dp/B001?smid=B456&ref=sr");
        assertThat(clave1).isNotEqualTo(clave2);
        assertThat(clave1).contains("smid=A123");
        assertThat(clave2).contains("smid=B456");
    }

    @Test
    void parametrosVarianteNoSeEliminan() {
        String clave1 = NormalizadorFicha.claveEnlace("https://ejemplo.com/p?color=rojo&size=M");
        String clave2 = NormalizadorFicha.claveEnlace("https://ejemplo.com/p?color=azul&size=M");
        assertThat(clave1).isNotEqualTo(clave2);
    }

    @Test
    void urlInvalidaSeComparaTalCual() {
        assertThat(NormalizadorFicha.claveEnlace("no es una url válida"))
                .isEqualTo("no es una url válida");
    }

    @Test
    void fragmentoSeIgnora() {
        String clave1 = NormalizadorFicha.claveEnlace("https://ejemplo.com/p#descripcion");
        String clave2 = NormalizadorFicha.claveEnlace("https://ejemplo.com/p#reviews");
        assertThat(clave1).isEqualTo(clave2);
        assertThat(clave1).isEqualTo("https://ejemplo.com/p");
    }
}