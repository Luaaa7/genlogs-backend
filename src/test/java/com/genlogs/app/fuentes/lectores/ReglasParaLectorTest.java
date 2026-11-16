package com.genlogs.app.fuentes.lectores;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.genlogs.app.fuentes.config.CampoSelector;
import com.genlogs.app.fuentes.config.PaginaSelector;
import com.genlogs.app.fuentes.config.SelectorFuenteDto;

/** ME: los selectores de fuente_selector llegan al servicio de lectura en su formato. */
class ReglasParaLectorTest {

    @Test
    void separaElAtributoSoloSiElServicioLoAcepta() {
        assertThat(ReglasParaLector.campo("img@src")).isEqualTo(Map.of("selector", "img", "atributo", "src"));
        assertThat(ReglasParaLector.campo(" a.link @ HREF ")).isEqualTo(Map.of("selector", "a.link", "atributo", "href"));
        assertThat(ReglasParaLector.campo("h3.titulo")).isEqualTo(Map.of("selector", "h3.titulo"));
        assertThat(ReglasParaLector.campo("a@title")).isEqualTo(Map.of("selector", "a@title"));
    }

    @Test
    void usaSoloLaPaginaPedidaYElItemEsElContenedor() {
        List<SelectorFuenteDto> selectores = List.of(
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.ITEM, "li.s-item@href"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.MONEDA, ".moneda"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.DESCRIPCION, "   "),
                new SelectorFuenteDto(PaginaSelector.FICHA, CampoSelector.NOMBRE, "h1"));

        Map<String, Object> reglas = ReglasParaLector.de(selectores, PaginaSelector.BUSQUEDA, "usd", null);

        assertThat(reglas).containsEntry("contenedor", "li.s-item")
                .containsEntry("moneda", Map.of("selector", ".moneda"))
                .containsEntry("codigoMoneda", "USD")
                .doesNotContainKeys("nombre", "descripcion", "separadorDecimal");
        assertThat(ReglasParaLector.de(null, PaginaSelector.FICHA, null, null)).isEmpty();
    }

    @Test
    void diceSiAlcanzanParaBuscarOParaLeerUnaFicha() {
        Map<String, Object> busqueda = Map.of("contenedor", "li", "nombre", Map.of(), "precio", Map.of(),
                "enlace", Map.of());
        assertThat(ReglasParaLector.sirveParaBuscar(busqueda)).isTrue();
        assertThat(ReglasParaLector.sirveParaBuscar(Map.of("nombre", Map.of(), "precio", Map.of()))).isFalse();
        assertThat(ReglasParaLector.sirveParaFicha(Map.of("nombre", Map.of(), "precio", Map.of()))).isTrue();
        assertThat(ReglasParaLector.sirveParaFicha(Map.of("nombre", Map.of()))).isFalse();
    }
}