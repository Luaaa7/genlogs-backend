package com.genlogs.app.fuentes.lectores;

import static org.assertj.core.api.Assertions.assertThat;
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

/** ME: el lector de eBay solo traduce entre el despachador (9-B.1) y el servicio de lectura. */
class EbayLectorTest {

    private static final String SESION =
            "{\"validaHasta\":\"2026-11-15T20:00:00Z\",\"storageState\":{\"cookies\":[],\"origins\":[]}}";

    private LectorRemotoClient cliente;
    private EbayLector lector;
    private ConsultaBusqueda consulta;

    @BeforeEach
    void preparar() {
        cliente = mock(LectorRemotoClient.class);
        lector = new EbayLector(cliente, Clock.fixed(Instant.parse("2026-11-15T17:00:00Z"), ZoneOffset.UTC));
        consulta = new ConsultaBusqueda(MetodoBusqueda.NUMERO_PARTE, "1R-0750", "CAT", null, null, null);
    }

    private static List<SelectorFuenteDto> reglasEbay() {
        return List.of(
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.ITEM, "li.s-item"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.NOMBRE, ".s-item__title"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.PRECIO, ".s-item__price"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.ENLACE, "a.s-item__link@href"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.IMAGEN, "img@src"),
                new SelectorFuenteDto(PaginaSelector.FICHA, CampoSelector.NOMBRE, "h1.x-item-title"),
                new SelectorFuenteDto(PaginaSelector.FICHA, CampoSelector.PRECIO, ".x-price-primary"));
    }

    private static ContratoLector.Respuesta respuesta(ContratoLector.Resultado... resultados) {
        return new ContratoLector.Respuesta(ContratoLector.Estado.OK, List.of(resultados), null);
    }

    @Test
    void declaraSoloEbay() {
        assertThat(lector.fuentes()).isEqualTo(Set.of(CodigoFuente.EBAY));
    }

    @Test
    void armaLaBusquedaDeEbayYDevuelveLasFichas() {
        when(cliente.buscar(any())).thenReturn(respuesta(new ContratoLector.Resultado("Filtro CAT", "Nuevo",
                new BigDecimal("1234.50"), "USD", "https://i.ebayimg.com/a.jpg", "EBAY",
                "https://www.ebay.com/itm/123")));

        ResultadoLectura r = lector.buscar(CodigoFuente.EBAY, "https://www.ebay.com/", SESION, consulta, reglasEbay());

        ArgumentCaptor<ContratoLector.Peticion> peticion = ArgumentCaptor.forClass(ContratoLector.Peticion.class);
        verify(cliente).buscar(peticion.capture());
        ContratoLector.Peticion p = peticion.getValue();
        assertThat(p.fuente()).isEqualTo("EBAY");
        assertThat(p.url()).isEqualTo("https://www.ebay.com/sch/i.html?_nkw=CAT+1R-0750");
        assertThat(p.maxResultados()).isEqualTo(8);
        assertThat(p.credenciales()).isNull();
        assertThat(p.reglas()).containsEntry("contenedor", "li.s-item")
                .containsEntry("enlace", Map.of("selector", "a.s-item__link", "atributo", "href"))
                .containsEntry("codigoMoneda", "USD")
                .containsEntry("separadorDecimal", ".")
                .containsEntry("sinResultados", ".srp-save-null-search")
                .doesNotContainKey("descripcion");
        assertThat(p.sesion().validaHasta()).isEqualTo("2026-11-15T20:00:00Z");
        assertThat(p.sesion().storageState().path("cookies").isArray()).isTrue();

        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.OK);
        assertThat(r.fichas()).containsExactly(new FichaFuente("Filtro CAT", "Nuevo", "1234.50", "USD",
                "https://i.ebayimg.com/a.jpg", "https://www.ebay.com/itm/123"));
    }

    @Test
    void sinFichasEsSinResultados() {
        when(cliente.buscar(any())).thenReturn(respuesta());
        ResultadoLectura r = lector.buscar(CodigoFuente.EBAY, null, SESION, consulta, reglasEbay());
        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.SIN_RESULTADOS);
    }

    @Test
    void sinReglasCompletasNoLlamaAlServicio() {
        List<SelectorFuenteDto> sinEnlace = List.of(
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.ITEM, "li.s-item"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.NOMBRE, ".s-item__title"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.PRECIO, ".s-item__price"));

        ResultadoLectura r = lector.buscar(CodigoFuente.EBAY, null, SESION, consulta, sinEnlace);

        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.ERROR);
        assertThat(r.reintentable()).isFalse();
        assertThat(r.mensaje()).contains("ENLACE");
        verifyNoInteractions(cliente);
    }

    @Test
    void laBusquedaPorImagenNoConsultaEbay() {
        ConsultaBusqueda imagen = new ConsultaBusqueda(MetodoBusqueda.IMAGEN, null, null, null, null,
                "https://x.com/a.jpg");
        ResultadoLectura r = lector.buscar(CodigoFuente.EBAY, null, SESION, imagen, reglasEbay());
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

        ResultadoLectura r = lector.buscar(CodigoFuente.EBAY, null, SESION, consulta, reglasEbay());

        assertThat(r.tipo()).isEqualTo(tipo);
        assertThat(r.reintentable()).isEqualTo(reintentable);
        assertThat(r.mensaje()).startsWith("eBay: ");
    }

    @Test
    void unaSesionQueNoEsJsonNoLlegaAlServicio() {
        ResultadoLectura r = lector.buscar(CodigoFuente.EBAY, null, "no-es-json", consulta, reglasEbay());
        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.ERROR);
        assertThat(r.mensaje()).doesNotContain("no-es-json");
        verifyNoInteractions(cliente);
    }

    @Test
    void leerUrlUsaLasReglasDeFicha() {
        when(cliente.leerFicha(any())).thenReturn(respuesta(new ContratoLector.Resultado("Filtro CAT", null,
                new BigDecimal("12.00"), "USD", null, "EBAY", "https://www.ebay.com/itm/123")));

        ResultadoLectura r = lector.leerUrl(CodigoFuente.EBAY, " https://www.ebay.com/itm/123 ", SESION, reglasEbay());

        ArgumentCaptor<ContratoLector.Peticion> peticion = ArgumentCaptor.forClass(ContratoLector.Peticion.class);
        verify(cliente).leerFicha(peticion.capture());
        assertThat(peticion.getValue().url()).isEqualTo("https://www.ebay.com/itm/123");
        assertThat(peticion.getValue().maxResultados()).isEqualTo(1);
        assertThat(peticion.getValue().reglas())
                .containsEntry("nombre", Map.of("selector", "h1.x-item-title"))
                .doesNotContainKey("contenedor");
        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.OK);
        assertThat(r.fichas().get(0).precio()).isEqualTo("12.00");
    }

    @Test
    void otraTiendaNoSeAtiende() {
        ResultadoLectura r = lector.buscar(CodigoFuente.AMAZON, null, SESION, consulta, reglasEbay());
        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.ERROR);
        verifyNoInteractions(cliente);
    }

    @Test
    void armaElTextoDeBusquedaSegunElMetodo() {
        assertThat(EbayLector.textoDeBusqueda(consulta)).isEqualTo("CAT 1R-0750");
        assertThat(EbayLector.textoDeBusqueda(new ConsultaBusqueda(MetodoBusqueda.GTIN, null, null,
                "0123456789012", null, null))).isEqualTo("0123456789012");
        assertThat(EbayLector.textoDeBusqueda(new ConsultaBusqueda(MetodoBusqueda.PALABRAS, null, "  Komatsu ",
                null, "bomba   hidraulica", null))).isEqualTo("Komatsu bomba hidraulica");
        assertThat(EbayLector.textoDeBusqueda(null)).isNull();
        assertThat(EbayLector.urlBusqueda(null, "a b&c")).isEqualTo("https://www.ebay.com/sch/i.html?_nkw=a+b%26c");
    }
}