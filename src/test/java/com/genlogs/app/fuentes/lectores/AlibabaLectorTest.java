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
import com.genlogs.app.fuentes.config.PaginaSelector;
import com.genlogs.app.fuentes.config.ResultadoLectura;
import com.genlogs.app.fuentes.config.SelectorFuenteDto;
import com.genlogs.app.sourcing.ConsultaBusqueda;
import com.genlogs.app.sourcing.MetodoBusqueda;
import com.genlogs.app.sourcing.lector.ContratoLector;
import com.genlogs.app.sourcing.lector.LectorRemotoClient;
import com.genlogs.app.sourcing.lector.LectorRemotoException;

/** KA: el lector de Alibaba traduce entre el despachador (9-B.1) y el servicio de lectura, con rangos de precio. */
class AlibabaLectorTest {

    private static final String SESION =
            "{\"validaHasta\":\"2026-11-16T20:00:00Z\",\"storageState\":{\"cookies\":[],\"origins\":[]}}";

    private LectorRemotoClient cliente;
    private AlibabaLector lector;
    private ConsultaBusqueda consulta;

    @BeforeEach
    void preparar() {
        cliente = mock(LectorRemotoClient.class);
        lector = new AlibabaLector(cliente, Clock.fixed(Instant.parse("2026-11-16T17:00:00Z"), ZoneOffset.UTC));
        consulta = new ConsultaBusqueda(MetodoBusqueda.NUMERO_PARTE, "1R-0750", "CAT", null, null, null);
    }

    private static List<SelectorFuenteDto> reglasAlibaba() {
        return List.of(
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.ITEM, ".organic-list-offer"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.NOMBRE, ".title"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.PRECIO, ".price"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.ENLACE, "a@href"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.IMAGEN, "img@src"),
                new SelectorFuenteDto(PaginaSelector.FICHA, CampoSelector.NOMBRE, "h1"),
                new SelectorFuenteDto(PaginaSelector.FICHA, CampoSelector.PRECIO, ".price-range"));
    }

    private static ContratoLector.Respuesta respuesta(ContratoLector.Resultado... resultados) {
        return new ContratoLector.Respuesta(ContratoLector.Estado.OK, List.of(resultados), null);
    }

    @Test
    void declaraSoloAlibaba() {
        assertThat(lector.fuentes()).isEqualTo(Set.of(CodigoFuente.ALIBABA));
    }

    @Test
    void pideElMayorDelRangoYLoAnotaEnLaDescripcion() {
        when(cliente.buscar(any())).thenReturn(respuesta(
                new ContratoLector.Resultado("Filtro CAT 1R-0750", "US$12.50-15.00", new BigDecimal("15.00"), "USD",
                        "https://s.alicdn.com/a.jpg", "ALIBABA", "https://www.alibaba.com/product-detail/1.html"),
                new ContratoLector.Resultado("Filtro de combustible CAT 1R0750", "US$40.00", new BigDecimal("40.00"),
                        "USD", null, "ALIBABA", "https://www.alibaba.com/product-detail/2.html")));

        ResultadoLectura r = lector.buscar(CodigoFuente.ALIBABA, "https://www.alibaba.com/", SESION, consulta,
                reglasAlibaba());

        ArgumentCaptor<ContratoLector.Peticion> peticion = ArgumentCaptor.forClass(ContratoLector.Peticion.class);
        verify(cliente).buscar(peticion.capture());
        ContratoLector.Peticion p = peticion.getValue();
        assertThat(p.fuente()).isEqualTo("ALIBABA");
        assertThat(p.url()).isEqualTo("https://www.alibaba.com/trade/search?SearchText=CAT+1R-0750");
        assertThat(p.maxResultados()).isEqualTo(8);
        assertThat(p.credenciales()).isNull();
        assertThat(p.reglas()).containsEntry("precioRango", "mayor")
                .containsEntry("descripcion", Map.of("selector", ".price"))
                .containsEntry("codigoMoneda", "USD")
                .containsEntry("sinResultados", ".search-no-result")
                .containsEntry("verificacion", AlibabaLector.VERIFICACION);

        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.OK);
        assertThat(r.fichas()).hasSize(2);
        assertThat(r.fichas().get(0).precio()).isEqualTo("15.00");
        assertThat(r.fichas().get(0).descripcion())
                .isEqualTo("Precio en Alibaba por rango: US$12.50-15.00 (se toma el mayor)");
        // Un solo precio no aporta nada a la descripción.
        assertThat(r.fichas().get(1).precio()).isEqualTo("40.00");
        assertThat(r.fichas().get(1).descripcion()).isNull();
    }

    @Test
    void porNumeroDeParteSoloQuedanLasFichasQueLoContienen() {
        // Lo que devolvió Alibaba el 16/11/2026: productos sin relación junto a los buenos.
        when(cliente.buscar(any())).thenReturn(respuesta(
                new ContratoLector.Resultado("Fuel filter 1r 0750 for CAT", null, new BigDecimal("15.00"), "USD",
                        null, "ALIBABA", "https://www.alibaba.com/product-detail/1.html"),
                new ContratoLector.Resultado("Dog collar leather", null, new BigDecimal("2.00"), "USD",
                        null, "ALIBABA", "https://www.alibaba.com/product-detail/2.html"),
                new ContratoLector.Resultado("FILTRO 1R0750", null, new BigDecimal("9.00"), "USD",
                        null, "ALIBABA", "https://www.alibaba.com/product-detail/3.html")));

        ResultadoLectura r = lector.buscar(CodigoFuente.ALIBABA, null, SESION, consulta, reglasAlibaba());

        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.OK);
        assertThat(r.fichas()).extracting(f -> f.enlace()).containsExactly(
                "https://www.alibaba.com/product-detail/1.html", "https://www.alibaba.com/product-detail/3.html");
    }

    @Test
    void siNingunaFichaTieneElNumeroDeParteEsSinResultados() {
        when(cliente.buscar(any())).thenReturn(respuesta(
                new ContratoLector.Resultado("Dog collar leather", null, new BigDecimal("2.00"), "USD",
                        null, "ALIBABA", "https://www.alibaba.com/product-detail/2.html")));

        ResultadoLectura r = lector.buscar(CodigoFuente.ALIBABA, null, SESION, consulta, reglasAlibaba());

        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.SIN_RESULTADOS);
    }

    @Test
    void porPalabrasOGtinNoSeFiltra() {
        when(cliente.buscar(any())).thenReturn(respuesta(
                new ContratoLector.Resultado("Bomba hidraulica Komatsu", null, new BigDecimal("120.00"), "USD",
                        null, "ALIBABA", "https://www.alibaba.com/product-detail/4.html")));
        ConsultaBusqueda palabras = new ConsultaBusqueda(MetodoBusqueda.PALABRAS, null, "Komatsu", null,
                "bomba hidraulica", null);
        ConsultaBusqueda gtin = new ConsultaBusqueda(MetodoBusqueda.GTIN, null, null, "0123456789012", null, null);

        assertThat(lector.buscar(CodigoFuente.ALIBABA, null, SESION, palabras, reglasAlibaba()).fichas()).hasSize(1);
        assertThat(lector.buscar(CodigoFuente.ALIBABA, null, SESION, gtin, reglasAlibaba()).fichas()).hasSize(1);
    }

    @Test
    void comparaElNumeroDeParteSinGuionesEspaciosNiMayusculas() {
        assertThat(AlibabaLector.soloLetrasYNumeros("1R-0750")).isEqualTo("1r0750");
        assertThat(AlibabaLector.soloLetrasYNumeros("1R0750")).isEqualTo("1r0750");
        assertThat(AlibabaLector.soloLetrasYNumeros("1r 0750")).isEqualTo("1r0750");
        assertThat(AlibabaLector.soloLetrasYNumeros(null)).isEmpty();
    }

    @Test
    void reconoceLosRangosQueMuestraAlibaba() {
        assertThat(AlibabaLector.descripcionConRango("US$ 12.50 - 15.00")).contains("US$ 12.50 - 15.00");
        assertThat(AlibabaLector.descripcionConRango("12.50 – 15.00")).isNotNull();
        assertThat(AlibabaLector.descripcionConRango("$1,200.00~$1,450.00")).isNotNull();
        assertThat(AlibabaLector.descripcionConRango("US$40.00")).isNull();
        assertThat(AlibabaLector.descripcionConRango("  ")).isNull();
        assertThat(AlibabaLector.descripcionConRango(null)).isNull();
    }

    @Test
    void unResultadoSinPrecioQuedaVacioNuncaEnCero() {
        when(cliente.buscar(any())).thenReturn(respuesta(new ContratoLector.Resultado("Filtro 1R-0750 sin precio", null,
                null, "USD", null, "ALIBABA", "https://www.alibaba.com/product-detail/3.html")));

        ResultadoLectura r = lector.buscar(CodigoFuente.ALIBABA, null, SESION, consulta, reglasAlibaba());

        assertThat(r.fichas().get(0).precio()).isNull();
    }

    @Test
    void sinFichasEsSinResultados() {
        when(cliente.buscar(any())).thenReturn(respuesta());
        ResultadoLectura r = lector.buscar(CodigoFuente.ALIBABA, null, SESION, consulta, reglasAlibaba());
        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.SIN_RESULTADOS);
    }

    @Test
    void sinReglasCompletasNoLlamaAlServicio() {
        List<SelectorFuenteDto> sinPrecio = List.of(
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.ITEM, ".organic-list-offer"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.NOMBRE, ".title"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.ENLACE, "a@href"));

        ResultadoLectura r = lector.buscar(CodigoFuente.ALIBABA, null, SESION, consulta, sinPrecio);

        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.ERROR);
        assertThat(r.mensaje()).contains("PRECIO");
        verifyNoInteractions(cliente);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "VERIFICACION,     VERIFICACION, false",
            "BLOQUEO,          BLOQUEADA,    false",
            "NO_CONFIGURADO,   NO_CONECTADO, false",
            "TIEMPO_AGOTADO,   ERROR,        true",
            "REGLAS_INVALIDAS, ERROR,        false"
    })
    void devuelveElErrorSinMarcarNada(ContratoLector.Estado estado, ResultadoLectura.Tipo tipo,
                                      boolean reintentable) {
        when(cliente.buscar(any())).thenThrow(new LectorRemotoException(estado));

        ResultadoLectura r = lector.buscar(CodigoFuente.ALIBABA, null, SESION, consulta, reglasAlibaba());

        assertThat(r.tipo()).isEqualTo(tipo);
        assertThat(r.reintentable()).isEqualTo(reintentable);
        assertThat(r.mensaje()).startsWith("Alibaba: ");
    }

    @Test
    void leerUrlTambienTomaElMayorDelRangoYNoFiltra() {
        // La ficha leída por URL no se filtra por N.º de parte: el asistente eligió esa URL.
        when(cliente.leerFicha(any())).thenReturn(respuesta(new ContratoLector.Resultado("Filtro CAT", "US$9.80-11.20",
                new BigDecimal("11.20"), "USD", null, "ALIBABA", "https://www.alibaba.com/product-detail/1.html")));

        ResultadoLectura r = lector.leerUrl(CodigoFuente.ALIBABA, " https://www.alibaba.com/product-detail/1.html ",
                SESION, reglasAlibaba());

        ArgumentCaptor<ContratoLector.Peticion> peticion = ArgumentCaptor.forClass(ContratoLector.Peticion.class);
        verify(cliente).leerFicha(peticion.capture());
        assertThat(peticion.getValue().url()).isEqualTo("https://www.alibaba.com/product-detail/1.html");
        assertThat(peticion.getValue().reglas())
                .containsEntry("precioRango", "mayor")
                .containsEntry("descripcion", Map.of("selector", ".price-range"))
                .doesNotContainKey("contenedor");
        assertThat(r.fichas().get(0).descripcion()).contains("US$9.80-11.20");
    }

    @Test
    void otraTiendaNoSeAtiende() {
        ResultadoLectura r = lector.buscar(CodigoFuente.MERCADO_LIBRE, null, SESION, consulta, reglasAlibaba());
        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.ERROR);
        verifyNoInteractions(cliente);
    }

    @Test
    void armaElTextoYLaUrlDeBusqueda() {
        assertThat(AlibabaLector.textoDeBusqueda(consulta)).isEqualTo("CAT 1R-0750");
        assertThat(AlibabaLector.textoDeBusqueda(new ConsultaBusqueda(MetodoBusqueda.IMAGEN, null, null, null, null,
                "https://x.com/a.jpg"))).isNull();
        assertThat(AlibabaLector.urlBusqueda(null, "a b&c"))
                .isEqualTo("https://www.alibaba.com/trade/search?SearchText=a+b%26c");
    }
}
