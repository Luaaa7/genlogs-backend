package com.genlogs.app.fuentes.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.genlogs.app.sourcing.ConsultaBusqueda;
import com.genlogs.app.sourcing.MetodoBusqueda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LectorFuenteDespachadorTest {

    private LectorFuenteEspecifico karla;
    private LectorFuenteEspecifico mell;
    private LectorFuenteEspecifico ale;
    private LectorFuenteDespachador despachador;
    private ConsultaBusqueda consulta;

    @BeforeEach
    void setUp() {
        karla = mock(LectorFuenteEspecifico.class, "karla");
        mell = mock(LectorFuenteEspecifico.class, "mell");
        ale = mock(LectorFuenteEspecifico.class, "ale");
        when(karla.fuentes()).thenReturn(Set.of(CodigoFuente.MERCADO_LIBRE, CodigoFuente.ALIBABA));
        when(mell.fuentes()).thenReturn(Set.of(CodigoFuente.EBAY));
        when(ale.fuentes()).thenReturn(Set.of(CodigoFuente.AMAZON, CodigoFuente.ALIEXPRESS));
        despachador = new LectorFuenteDespachador(List.of(karla, mell, ale));
        consulta = new ConsultaBusqueda(MetodoBusqueda.NUMERO_PARTE, "1R-0750", "Caterpillar",
                null, null, null);
    }

    @Test
    void mercadoLibreDelegaEnKarla() {
        assertDelega(CodigoFuente.MERCADO_LIBRE, karla, mell, ale);
    }

    @Test
    void alibabaDelegaEnKarla() {
        assertDelega(CodigoFuente.ALIBABA, karla, mell, ale);
    }

    @Test
    void ebayDelegaEnMell() {
        assertDelega(CodigoFuente.EBAY, mell, karla, ale);
    }

    @Test
    void amazonDelegaEnAle() {
        assertDelega(CodigoFuente.AMAZON, ale, karla, mell);
    }

    @Test
    void aliExpressDelegaEnAle() {
        assertDelega(CodigoFuente.ALIEXPRESS, ale, karla, mell);
    }

    @Test
    void fuenteSinLectorDevuelveNoConectado() {
        LectorFuenteDespachador soloKarla = new LectorFuenteDespachador(List.of(karla));

        ResultadoLectura ebay = soloKarla.buscar(CodigoFuente.EBAY, "https://ebay", "sesion",
                consulta, List.of());
        ResultadoLectura amazon = soloKarla.leerUrl(CodigoFuente.AMAZON, "https://amazon/ficha",
                "sesion", List.of());

        assertThat(ebay.tipo()).isEqualTo(ResultadoLectura.Tipo.NO_CONECTADO);
        assertThat(ebay.reintentable()).isFalse();
        assertThat(ebay.fichas()).isEmpty();
        assertThat(ebay.mensaje()).contains("EBAY");
        assertThat(amazon.tipo()).isEqualTo(ResultadoLectura.Tipo.NO_CONECTADO);
        assertThat(amazon.mensaje()).contains("AMAZON");
        verify(karla, never()).buscar(any(), any(), any(), any(), any());
        verify(karla, never()).leerUrl(any(), any(), any(), any());
    }

    @Test
    void sinNingunLectorTodasLasTiendasQuedanNoConectadas() {
        LectorFuenteDespachador vacio = new LectorFuenteDespachador(List.of());

        for (CodigoFuente codigo : CodigoFuente.values()) {
            ResultadoLectura r = vacio.buscar(codigo, "https://tienda", "sesion", consulta, List.of());
            assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.NO_CONECTADO);
            assertThat(r.reintentable()).isFalse();
        }
    }

    @Test
    void elResultadoDelLectorSeConservaSinModificaciones() {
        ResultadoLectura esperado = ResultadoLectura.verificacion("La tienda pidió un CAPTCHA al buscar.");
        when(mell.buscar(any(), any(), any(), any(), any())).thenReturn(esperado);

        ResultadoLectura r = despachador.buscar(CodigoFuente.EBAY, "https://ebay", "sesion-opaca",
                consulta, List.of());

        assertThat(r).isSameAs(esperado);
        assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.VERIFICACION);
        assertThat(r.mensaje()).isEqualTo("La tienda pidió un CAPTCHA al buscar.");
    }

    @Test
    void unaFuenteNoSeEnviaAlLectorIncorrecto() {
        when(ale.buscar(any(), any(), any(), any(), any())).thenReturn(ResultadoLectura.sinResultados());

        despachador.buscar(CodigoFuente.AMAZON, "https://amazon", "sesion-ale", consulta, List.of());

        verify(ale).buscar(eq(CodigoFuente.AMAZON), eq("https://amazon"), eq("sesion-ale"),
                eq(consulta), eq(List.of()));
        verify(karla, never()).buscar(eq(CodigoFuente.AMAZON), any(), any(), any(), any());
        verify(mell, never()).buscar(eq(CodigoFuente.AMAZON), any(), any(), any(), any());
        verify(ale, never()).buscar(eq(CodigoFuente.ALIEXPRESS), any(), any(), any(), any());
    }

    @Test
    void dosLectoresParaElMismoCodigoSonErrorDeConfiguracion() {
        LectorFuenteEspecifico primero = new LectorFijo(Set.of(CodigoFuente.EBAY));
        LectorFuenteEspecifico segundo = new LectorFijo(Set.of(CodigoFuente.EBAY));

        assertThatThrownBy(() -> new LectorFuenteDespachador(List.of(primero, segundo)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Dos lectores registrados para EBAY")
                .hasMessageContaining("LectorFijo")
                .hasMessageContaining("Cada CodigoFuente admite un solo lector");
    }

    @Test
    void unLectorSinFuentesEsErrorDeConfiguracion() {
        when(karla.fuentes()).thenReturn(Set.of());

        assertThatThrownBy(() -> new LectorFuenteDespachador(List.of(karla)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no declaró ninguna CodigoFuente");
    }

    @Test
    void elDespachadorNuncaSeInyectaASiMismo() throws Exception {
        assertThat(LectorFuenteEspecifico.class.isAssignableFrom(LectorFuenteDespachador.class)).isFalse();

        Constructor<?> spring = LectorFuenteDespachador.class.getConstructor(ObjectProvider.class);
        Type parametro = spring.getGenericParameterTypes()[0];
        assertThat(parametro).isInstanceOf(ParameterizedType.class);
        Type argumento = ((ParameterizedType) parametro).getActualTypeArguments()[0];
        assertThat(argumento).isEqualTo(LectorFuenteEspecifico.class);

        assertThat(despachador.lectorDe(CodigoFuente.EBAY)).isSameAs(mell);
        assertThat(despachador.lectorDe(CodigoFuente.MERCADO_LIBRE)).isSameAs(karla);
        for (CodigoFuente codigo : CodigoFuente.values()) {
            assertThat(despachador.lectorDe(codigo)).isNotInstanceOf(LectorFuenteDespachador.class);
        }
    }

    private void assertDelega(CodigoFuente codigo, LectorFuenteEspecifico esperado,
                              LectorFuenteEspecifico... otros) {
        ResultadoLectura busqueda = ResultadoLectura.ok(List.of(
                new FichaFuente("filtro", "desc", "10.00", "USD", null, "https://ficha")));
        ResultadoLectura url = ResultadoLectura.bloqueada("bloqueada");
        when(esperado.buscar(any(), any(), any(), any(), any())).thenReturn(busqueda);
        when(esperado.leerUrl(any(), any(), any(), any())).thenReturn(url);

        assertThat(despachador.buscar(codigo, "https://tienda", "sesion-" + codigo, consulta, List.of()))
                .isSameAs(busqueda);
        assertThat(despachador.leerUrl(codigo, "https://ficha", "sesion-" + codigo, List.of()))
                .isSameAs(url);

        verify(esperado).buscar(eq(codigo), eq("https://tienda"), eq("sesion-" + codigo),
                eq(consulta), eq(List.of()));
        verify(esperado).leerUrl(eq(codigo), eq("https://ficha"), eq("sesion-" + codigo), eq(List.of()));
        for (LectorFuenteEspecifico otro : otros) {
            verify(otro, never()).buscar(any(), any(), any(), any(), any());
            verify(otro, never()).leerUrl(any(), any(), any(), any());
        }
    }

    /** Doble de prueba con nombre de clase estable para el mensaje de conflicto. */
    private static final class LectorFijo implements LectorFuenteEspecifico {
        private final Set<CodigoFuente> codigos;

        private LectorFijo(Set<CodigoFuente> codigos) {
            this.codigos = codigos;
        }

        @Override
        public Set<CodigoFuente> fuentes() {
            return codigos;
        }

        @Override
        public ResultadoLectura buscar(CodigoFuente codigo, String urlBase, String sesion,
                                       ConsultaBusqueda consulta, List<SelectorFuenteDto> selectores) {
            return ResultadoLectura.sinResultados();
        }

        @Override
        public ResultadoLectura leerUrl(CodigoFuente codigo, String url, String sesion,
                                        List<SelectorFuenteDto> selectores) {
            return ResultadoLectura.sinResultados();
        }
    }
}
