package com.genlogs.app.fuentes.lectores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import com.genlogs.app.fuentes.config.CampoSelector;
import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.FichaFuente;
import com.genlogs.app.fuentes.config.PaginaSelector;
import com.genlogs.app.fuentes.config.ResultadoLectura;
import com.genlogs.app.fuentes.config.SelectorFuenteDto;
import com.genlogs.app.sourcing.ConsultaBusqueda;
import com.genlogs.app.sourcing.MetodoBusqueda;
import com.genlogs.app.sourcing.lector.ContratoLector;
import com.genlogs.app.sourcing.lector.LectorRemotoClient;
import com.genlogs.app.sourcing.lector.LectorRemotoException;
import com.genlogs.lector.LectorChromium;

/** El lector de Amazon y AliExpress solo traduce entre el despachador (9-B.1) y el servicio de lectura. */
class AmazonAliExpressLectorTest {

    private static final String SESION =
            "{\"validaHasta\":\"2026-11-15T20:00:00Z\",\"storageState\":{\"cookies\":[],\"origins\":[]}}";

    private LectorRemotoClient cliente;
    private AmazonAliExpressLector lector;
    private ConsultaBusqueda consulta;

    @BeforeEach
    void preparar() {
        cliente = mock(LectorRemotoClient.class);
        lector = new AmazonAliExpressLector(cliente, Clock.fixed(Instant.parse("2026-11-15T17:00:00Z"), ZoneOffset.UTC));
        consulta = new ConsultaBusqueda(MetodoBusqueda.NUMERO_PARTE, "1R-0750", "CAT", null, null, null);
    }

    private static List<SelectorFuenteDto> reglas() {
        return List.of(
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.ITEM, "div.item"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.NOMBRE, ".nombre"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.PRECIO, ".precio"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.ENLACE, "a.ficha@href"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.IMAGEN, "img@src"),
                new SelectorFuenteDto(PaginaSelector.FICHA, CampoSelector.NOMBRE, "h1"),
                new SelectorFuenteDto(PaginaSelector.FICHA, CampoSelector.PRECIO, ".precio-ficha"));
    }

    private static ContratoLector.Respuesta respuesta(ContratoLector.Resultado... resultados) {
        return new ContratoLector.Respuesta(ContratoLector.Estado.OK, List.of(resultados), null);
    }

    @Test
    void declaraAmazonYAliExpress() {
        assertThat(lector.fuentes()).isEqualTo(Set.of(CodigoFuente.AMAZON, CodigoFuente.ALIEXPRESS));
    }

    @Test
    void armaLaBusquedaDeAmazonYDevuelveLasFichas() {
        when(cliente.buscar(any())).thenReturn(respuesta(new ContratoLector.Resultado("Filtro CAT", "Nuevo",
                new BigDecimal("1234.50"), "USD", "https://m.media-amazon.com/a.jpg", "AMAZON",
                "https://www.amazon.com/dp/B001")));

        ResultadoLectura r = lector.buscar(CodigoFuente.AMAZON, "https://www.amazon.com/", SESION, consulta, reglas());

        ArgumentCaptor<ContratoLector.Peticion> peticion = ArgumentCaptor.forClass(ContratoLector.Peticion.class);
        verify(cliente).buscar(peticion.capture());
        ContratoLector.Peticion p = peticion.getValue();
        assertThat(p.fuente()).isEqualTo("AMAZON");
        assertThat(p.url()).isEqualTo("https://www.amazon.com/s?k=CAT+1R-0750");
        assertThat(p.maxResultados()).isEqualTo(8);
        assertThat(p.credenciales()).isNull();
        assertThat(p.reglas()).containsEntry("contenedor", "div.item")
                .containsEntry("enlace", Map.of("selector", "a.ficha", "atributo", "href"))
                .containsEntry("codigoMoneda", "USD")
                .containsEntry("separadorDecimal", ".")
                .containsEntry("sinResultados", "[data-csa-c-content-id=\"messaging-messages-no-results\"]")
                .doesNotContainKey("descripcion");
        assertThat(p.sesion().validaHasta()).isEqualTo("2026-11-15T20:00:00Z");

        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.OK);
        assertThat(r.fichas()).containsExactly(new FichaFuente("Filtro CAT", "Nuevo", "1234.50", "USD",
                "https://m.media-amazon.com/a.jpg", "https://www.amazon.com/dp/B001"));
    }

    @Test
    void laBusquedaVaciaDeAmazonEsElBloqueDeMensajes() {
        String html = """
                <html><body>
                  <div data-csa-c-content-id="messaging-messages-no-results">
                    <div class="s-no-outline">No results for</div>
                  </div>
                </body></html>
                """;
        assertThat(LectorChromium.extraer(html, "https://www.amazon.com/s?k=zzz", peticionAmazon(), true)).isEmpty();
    }

    @Test
    void losEnlacesConSNoOutlineNoSonUnaBusquedaVacia() {
        String html = """
                <html><body>
                  <a class="s-no-outline" href="https://www.amazon.com/dp/B001">Filtro</a>
                  <a class="s-no-outline" href="https://www.amazon.com/dp/B002">Aceite</a>
                </body></html>
                """;
        assertThatThrownBy(() -> LectorChromium.extraer(html, "https://www.amazon.com/s?k=oil", peticionAmazon(), true))
                .isInstanceOf(LectorRemotoException.class)
                .extracting(e -> ((LectorRemotoException) e).getEstado())
                .isEqualTo(ContratoLector.Estado.REGLAS_INVALIDAS);
    }

    private static ContratoLector.Peticion peticionAmazon() {
        return new ContratoLector.Peticion("AMAZON", "https://www.amazon.com/s?k=oil",
                Map.of(
                        "contenedor", "div.s-result-item",
                        "nombre", Map.of("selector", "h2"),
                        "precio", Map.of("selector", ".a-price"),
                        "enlace", Map.of("selector", "a.s-no-outline", "atributo", "href"),
                        "codigoMoneda", "USD",
                        "separadorDecimal", ".",
                        "sinResultados", AmazonAliExpressLector.SIN_RESULTADOS_AMAZON),
                null, null, 8);
    }

    @Test
    void armaLaBusquedaDeAliExpress() {
        when(cliente.buscar(any())).thenReturn(respuesta());

        lector.buscar(CodigoFuente.ALIEXPRESS, "https://www.aliexpress.com/", SESION, consulta, reglas());

        ArgumentCaptor<ContratoLector.Peticion> peticion = ArgumentCaptor.forClass(ContratoLector.Peticion.class);
        verify(cliente).buscar(peticion.capture());
        assertThat(peticion.getValue().fuente()).isEqualTo("ALIEXPRESS");
        assertThat(peticion.getValue().url())
                .isEqualTo("https://www.aliexpress.com/wholesale?SearchText=CAT+1R-0750");
        assertThat(peticion.getValue().reglas())
                .containsEntry("codigoMoneda", "USD")
                .containsEntry("separadorDecimal", ".")
                .containsEntry("sinResultados", ".search-no-result");
    }

    @Test
    void sinBaseUsaLaDeLaTienda() {
        when(cliente.buscar(any())).thenReturn(respuesta());

        lector.buscar(CodigoFuente.AMAZON, null, SESION, consulta, reglas());
        lector.buscar(CodigoFuente.ALIEXPRESS, "  ", SESION, consulta, reglas());

        ArgumentCaptor<ContratoLector.Peticion> peticion = ArgumentCaptor.forClass(ContratoLector.Peticion.class);
        verify(cliente, org.mockito.Mockito.times(2)).buscar(peticion.capture());
        assertThat(peticion.getAllValues().get(0).url()).startsWith("https://www.amazon.com/s?k=");
        assertThat(peticion.getAllValues().get(1).url()).startsWith("https://www.aliexpress.com/wholesale?SearchText=");
    }

    @Test
    void sinFichasEsSinResultados() {
        when(cliente.buscar(any())).thenReturn(respuesta());
        ResultadoLectura r = lector.buscar(CodigoFuente.AMAZON, null, SESION, consulta, reglas());
        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.SIN_RESULTADOS);
    }

    @Test
    void sinReglasCompletasNoLlamaAlServicioYNombraLaTienda() {
        List<SelectorFuenteDto> sinEnlace = List.of(
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.ITEM, "div.item"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.NOMBRE, ".nombre"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.PRECIO, ".precio"));

        ResultadoLectura amazon = lector.buscar(CodigoFuente.AMAZON, null, SESION, consulta, sinEnlace);
        ResultadoLectura ali = lector.buscar(CodigoFuente.ALIEXPRESS, null, SESION, consulta, sinEnlace);

        assertThat(amazon.tipo()).isEqualTo(ResultadoLectura.Tipo.ERROR);
        assertThat(amazon.reintentable()).isFalse();
        assertThat(amazon.mensaje()).contains("Amazon").contains("ENLACE");
        assertThat(ali.mensaje()).contains("AliExpress").contains("ENLACE");
        verifyNoInteractions(cliente);
    }

    @Test
    void laBusquedaPorImagenNoConsultaLaTienda() {
        ConsultaBusqueda imagen = new ConsultaBusqueda(MetodoBusqueda.IMAGEN, null, null, null, null,
                "https://x.com/a.jpg");
        ResultadoLectura r = lector.buscar(CodigoFuente.ALIEXPRESS, null, SESION, imagen, reglas());
        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.SIN_RESULTADOS);
        verifyNoInteractions(cliente);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "VERIFICACION,     VERIFICACION, false",
            "BLOQUEO,          BLOQUEADA,    false",
            "NO_CONFIGURADO,   NO_CONECTADO, false",
            "TIEMPO_AGOTADO,   ERROR,        true",
            "SIN_RESPUESTA,    ERROR,        true",
            "REGLAS_INVALIDAS, ERROR,        false",
            "SESION_VENCIDA,   ERROR,        false"
    })
    void traduceLoQueRespondeElServicio(ContratoLector.Estado estado, ResultadoLectura.Tipo tipo,
                                        boolean reintentable) {
        when(cliente.buscar(any())).thenThrow(new LectorRemotoException(estado));

        ResultadoLectura r = lector.buscar(CodigoFuente.AMAZON, null, SESION, consulta, reglas());

        assertThat(r.tipo()).isEqualTo(tipo);
        assertThat(r.reintentable()).isEqualTo(reintentable);
        assertThat(r.mensaje()).startsWith("Amazon: ");
    }

    @Test
    void unaSesionQueNoEsJsonNoLlegaAlServicio() {
        ResultadoLectura r = lector.buscar(CodigoFuente.ALIEXPRESS, null, "no-es-json", consulta, reglas());
        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.ERROR);
        assertThat(r.mensaje()).contains("AliExpress").doesNotContain("no-es-json");
        verifyNoInteractions(cliente);
    }

    @Test
    void leerUrlUsaLasReglasDeFicha() {
        when(cliente.leerFicha(any())).thenReturn(respuesta(new ContratoLector.Resultado("Filtro CAT", null,
                new BigDecimal("12.00"), "USD", null, "AMAZON", "https://www.amazon.com/dp/B001")));

        ResultadoLectura r = lector.leerUrl(CodigoFuente.AMAZON, " https://www.amazon.com/dp/B001 ", SESION, reglas());

        ArgumentCaptor<ContratoLector.Peticion> peticion = ArgumentCaptor.forClass(ContratoLector.Peticion.class);
        verify(cliente).leerFicha(peticion.capture());
        assertThat(peticion.getValue().url()).isEqualTo("https://www.amazon.com/dp/B001");
        assertThat(peticion.getValue().fuente()).isEqualTo("AMAZON");
        assertThat(peticion.getValue().maxResultados()).isEqualTo(1);
        assertThat(peticion.getValue().reglas())
                .containsEntry("nombre", Map.of("selector", "h1"))
                .containsEntry("codigoMoneda", "USD")
                .containsEntry("separadorDecimal", ".")
                .doesNotContainKey("contenedor");
        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.OK);
        assertThat(r.fichas().get(0).precio()).isEqualTo("12.00");
    }

    @Test
    void otraTiendaNoSeAtiende() {
        ResultadoLectura r = lector.buscar(CodigoFuente.EBAY, null, SESION, consulta, reglas());
        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.ERROR);
        assertThat(r.mensaje()).contains("Amazon y AliExpress");
        verifyNoInteractions(cliente);
    }

    @Test
    void armaElTextoYLaUrlDeCadaTienda() {
        assertThat(PuenteLector.textoDeBusqueda(consulta)).isEqualTo("CAT 1R-0750");
        assertThat(PuenteLector.textoDeBusqueda(null)).isNull();
        assertThat(AmazonAliExpressLector.urlBusqueda(CodigoFuente.AMAZON, null, "a b&c"))
                .isEqualTo("https://www.amazon.com/s?k=a+b%26c");
        assertThat(AmazonAliExpressLector.urlBusqueda(CodigoFuente.ALIEXPRESS, null, "a b&c"))
                .isEqualTo("https://www.aliexpress.com/wholesale?SearchText=a+b%26c");
    }
}
