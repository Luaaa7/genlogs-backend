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
}
