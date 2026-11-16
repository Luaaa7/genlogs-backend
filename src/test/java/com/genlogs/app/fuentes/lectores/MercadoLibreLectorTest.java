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

/** KM: el lector de Mercado Libre solo traduce entre el despachador (9-B.1) y el servicio de lectura. */
class MercadoLibreLectorTest {

    private static final String SESION =
            "{\"validaHasta\":\"2026-11-16T20:00:00Z\",\"storageState\":{\"cookies\":[],\"origins\":[]}}";

    private LectorRemotoClient cliente;
    private MercadoLibreLector lector;
    private ConsultaBusqueda consulta;

    @BeforeEach
    void preparar() {
        cliente = mock(LectorRemotoClient.class);
        lector = new MercadoLibreLector(cliente, Clock.fixed(Instant.parse("2026-11-16T17:00:00Z"), ZoneOffset.UTC));
        consulta = new ConsultaBusqueda(MetodoBusqueda.NUMERO_PARTE, "1R-0750", "CAT", null, null, null);
    }

    private static List<SelectorFuenteDto> reglasMl() {
        return List.of(
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.ITEM, "li.ui-search-layout__item"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.NOMBRE, ".poly-component__title"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.PRECIO, ".andes-money-amount__fraction"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.ENLACE, "a.poly-component__title@href"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.IMAGEN, "img@data-src"),
                new SelectorFuenteDto(PaginaSelector.FICHA, CampoSelector.NOMBRE, "h1.ui-pdp-title"),
                new SelectorFuenteDto(PaginaSelector.FICHA, CampoSelector.PRECIO, ".ui-pdp-price__second-line .andes-money-amount__fraction"));
    }

    private static ContratoLector.Respuesta respuesta(ContratoLector.Resultado... resultados) {
        return new ContratoLector.Respuesta(ContratoLector.Estado.OK, List.of(resultados), null);
    }

    @Test
    void declaraSoloMercadoLibre() {
        assertThat(lector.fuentes()).isEqualTo(Set.of(CodigoFuente.MERCADO_LIBRE));
    }

    @Test
    void buscaPorMarcaYNumeroDeParteEnElListadoYDevuelveLasFichas() {
        when(cliente.buscar(any())).thenReturn(respuesta(new ContratoLector.Resultado("Filtro CAT 1R-0750", null,
                new BigDecimal("189.90"), "PEN", "https://http2.mlstatic.com/D_1.jpg", "MERCADO_LIBRE",
                "https://articulo.mercadolibre.com.pe/MPE-1")));

        ResultadoLectura r = lector.buscar(CodigoFuente.MERCADO_LIBRE, "https://www.mercadolibre.com.pe", SESION,
                consulta, reglasMl());

        ArgumentCaptor<ContratoLector.Peticion> peticion = ArgumentCaptor.forClass(ContratoLector.Peticion.class);
        verify(cliente).buscar(peticion.capture());
        ContratoLector.Peticion p = peticion.getValue();
        assertThat(p.fuente()).isEqualTo("MERCADO_LIBRE");
        assertThat(p.url()).isEqualTo("https://listado.mercadolibre.com.pe/cat-1r-0750");
        assertThat(p.maxResultados()).isEqualTo(8);
        // D-36: solo datos, nunca credenciales de vendedor.
        assertThat(p.credenciales()).isNull();
        assertThat(p.reglas()).containsEntry("contenedor", "li.ui-search-layout__item")
                .containsEntry("enlace", Map.of("selector", "a.poly-component__title", "atributo", "href"))
                .containsEntry("imagen", Map.of("selector", "img", "atributo", "data-src"))
                .containsEntry("codigoMoneda", "PEN")
                .containsEntry("separadorDecimal", ".")
                .containsEntry("sinResultados", ".ui-search-rescue")
                .containsEntry("verificacion", MercadoLibreLector.VERIFICACION);
        assertThat(p.sesion().validaHasta()).isEqualTo("2026-11-16T20:00:00Z");

        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.OK);
        assertThat(r.fichas()).containsExactly(new FichaFuente("Filtro CAT 1R-0750", null, "189.90", "PEN",
                "https://http2.mlstatic.com/D_1.jpg", "https://articulo.mercadolibre.com.pe/MPE-1"));
    }

    @Test
    void laVerificacionReconoceLaPaginaDeVerificacionDeCuenta() {
        // Estructura real de /gz/account-verification (16/11/2026), recortada.
        String verificacion = "<main id=\"root-app\"><div class=\"account-verification-main desktop-view\">"
                + "<div class=\"account-verification-header\"></div><div class=\"andes-card message-card\">"
                + "¡Hola! Para continuar, ingresa a tu cuenta</div></div></main>";
        String listado = "<ol><li class=\"ui-search-layout__item\"><a class=\"poly-component__title\""
                + " href=\"/MPE-1\">Filtro CAT 1R-0750</a></li></ol>";

        assertThat(org.jsoup.Jsoup.parse(verificacion).select(MercadoLibreLector.VERIFICACION)).hasSize(1);
        assertThat(org.jsoup.Jsoup.parse(listado).select(MercadoLibreLector.VERIFICACION)).isEmpty();
        // No choca con "sin resultados" y el servicio de lectura acepta el largo.
        assertThat(MercadoLibreLector.VERIFICACION).doesNotContain(MercadoLibreLector.SIN_RESULTADOS)
                .hasSizeLessThanOrEqualTo(500);
    }

    @Test
    void unResultadoSinPrecioQuedaVacioNuncaEnCero() {
        when(cliente.buscar(any())).thenReturn(respuesta(new ContratoLector.Resultado("Filtro sin precio", null,
                null, "PEN", null, "MERCADO_LIBRE", "https://articulo.mercadolibre.com.pe/MPE-2")));

        ResultadoLectura r = lector.buscar(CodigoFuente.MERCADO_LIBRE, null, SESION, consulta, reglasMl());

        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.OK);
        assertThat(r.fichas().get(0).precio()).isNull();
    }

    @Test
    void sinFichasEsSinResultados() {
        when(cliente.buscar(any())).thenReturn(respuesta());
        ResultadoLectura r = lector.buscar(CodigoFuente.MERCADO_LIBRE, null, SESION, consulta, reglasMl());
        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.SIN_RESULTADOS);
    }

    @Test
    void sinReglasCompletasNoLlamaAlServicio() {
        List<SelectorFuenteDto> sinEnlace = List.of(
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.ITEM, "li.ui-search-layout__item"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.NOMBRE, ".poly-component__title"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.PRECIO, ".andes-money-amount__fraction"));

        ResultadoLectura r = lector.buscar(CodigoFuente.MERCADO_LIBRE, null, SESION, consulta, sinEnlace);

        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.ERROR);
        assertThat(r.reintentable()).isFalse();
        assertThat(r.mensaje()).contains("ENLACE");
        verifyNoInteractions(cliente);
    }

    @Test
    void laBusquedaPorImagenNoConsultaMercadoLibre() {
        ConsultaBusqueda imagen = new ConsultaBusqueda(MetodoBusqueda.IMAGEN, null, null, null, null,
                "https://x.com/a.jpg");
        ResultadoLectura r = lector.buscar(CodigoFuente.MERCADO_LIBRE, null, SESION, imagen, reglasMl());
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
    void devuelveElErrorSinMarcarNada(ContratoLector.Estado estado, ResultadoLectura.Tipo tipo,
                                      boolean reintentable) {
        when(cliente.buscar(any())).thenThrow(new LectorRemotoException(estado));

        ResultadoLectura r = lector.buscar(CodigoFuente.MERCADO_LIBRE, null, SESION, consulta, reglasMl());

        assertThat(r.tipo()).isEqualTo(tipo);
        assertThat(r.reintentable()).isEqualTo(reintentable);
        assertThat(r.mensaje()).startsWith("Mercado Libre: ");
    }

    @Test
    void unaSesionQueNoEsJsonNoLlegaAlServicio() {
        ResultadoLectura r = lector.buscar(CodigoFuente.MERCADO_LIBRE, null, "no-es-json", consulta, reglasMl());
        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.ERROR);
        assertThat(r.mensaje()).doesNotContain("no-es-json");
        verifyNoInteractions(cliente);
    }

    @Test
    void leerUrlUsaLasReglasDeFicha() {
        when(cliente.leerFicha(any())).thenReturn(respuesta(new ContratoLector.Resultado("Filtro CAT", null,
                new BigDecimal("189.90"), "PEN", null, "MERCADO_LIBRE", "https://articulo.mercadolibre.com.pe/MPE-1")));

        ResultadoLectura r = lector.leerUrl(CodigoFuente.MERCADO_LIBRE, " https://articulo.mercadolibre.com.pe/MPE-1 ",
                SESION, reglasMl());

        ArgumentCaptor<ContratoLector.Peticion> peticion = ArgumentCaptor.forClass(ContratoLector.Peticion.class);
        verify(cliente).leerFicha(peticion.capture());
        assertThat(peticion.getValue().url()).isEqualTo("https://articulo.mercadolibre.com.pe/MPE-1");
        assertThat(peticion.getValue().maxResultados()).isEqualTo(1);
        assertThat(peticion.getValue().credenciales()).isNull();
        assertThat(peticion.getValue().reglas())
                .containsEntry("nombre", Map.of("selector", "h1.ui-pdp-title"))
                .containsKey("verificacion")
                .doesNotContainKey("contenedor")
                .doesNotContainKey("sinResultados");
        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.OK);
        assertThat(r.fichas().get(0).precio()).isEqualTo("189.90");
    }

    @Test
    void otraTiendaNoSeAtiende() {
        ResultadoLectura r = lector.buscar(CodigoFuente.ALIBABA, null, SESION, consulta, reglasMl());
        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.ERROR);
        verifyNoInteractions(cliente);
    }

    @Test
    void armaElTextoYLaUrlDelListado() {
        assertThat(MercadoLibreLector.textoDeBusqueda(consulta)).isEqualTo("CAT 1R-0750");
        assertThat(MercadoLibreLector.textoDeBusqueda(new ConsultaBusqueda(MetodoBusqueda.PALABRAS, null, "  Komatsu ",
                null, "bomba   hidraulica", null))).isEqualTo("Komatsu bomba hidraulica");
        assertThat(MercadoLibreLector.textoDeBusqueda(null)).isNull();
        assertThat(MercadoLibreLector.urlBusqueda(null, "Komatsu bomba hidraulica"))
                .isEqualTo("https://listado.mercadolibre.com.pe/komatsu-bomba-hidraulica");
        assertThat(MercadoLibreLector.urlBusqueda("https://listado.mercadolibre.com.pe/", "a&b ñ"))
                .isEqualTo("https://listado.mercadolibre.com.pe/a%26b-%C3%B1");
        assertThat(MercadoLibreLector.urlBusqueda("no es una url", "x"))
                .isEqualTo("https://listado.mercadolibre.com.pe/x");
    }
}
