package com.genlogs.app.fuentes.config;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import com.genlogs.app.sourcing.ConsultaBusqueda;
import com.genlogs.app.sourcing.ConsultaFuenteService;
import com.genlogs.app.sourcing.AvisoFuenteConError;
import com.genlogs.app.sourcing.PausaFuente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * El contexto de lectores arranca con cero, uno o varios {@link LectorFuenteEspecifico}.
 * El despachador no entra en esa lista.
 */
class LectorFuenteDespachadorContextoTest {

    private final ApplicationContextRunner base = new ApplicationContextRunner()
            .withUserConfiguration(LectorFuenteDespachador.class, LectorFuenteConfig.class);

    @Test
    void arrancaSinLectoresEspecificosYElServicioRecibeElDespachador() {
        base.withUserConfiguration(ConsultaMinima.class).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(LectorFuenteDespachador.class);
            assertThat(ctx.getBeansOfType(LectorFuente.class)).hasSize(1);
            assertThat(ctx.getBean(LectorFuente.class)).isInstanceOf(LectorFuenteDespachador.class);
            assertThat(ctx).doesNotHaveBean(LectorFuenteNoConectado.class);
            assertThat(ctx.getBeansOfType(LectorFuenteEspecifico.class)).isEmpty();

            LectorFuente lector = ctx.getBean(LectorFuente.class);
            ResultadoLectura r = lector.buscar(CodigoFuente.MERCADO_LIBRE, "https://ml", "sesion",
                    null, List.of());
            assertThat(r.tipo()).isEqualTo(ResultadoLectura.Tipo.NO_CONECTADO);

            ConsultaFuenteService servicio = ctx.getBean(ConsultaFuenteService.class);
            assertThat(ReflectionTestUtils.getField(servicio, "lector")).isSameAs(lector);
        });
    }

    @Test
    void arrancaConUnSoloLector() {
        base.withUserConfiguration(SoloEbay.class).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            LectorFuenteDespachador despachador = ctx.getBean(LectorFuenteDespachador.class);
            assertThat(despachador.lectorDe(CodigoFuente.EBAY)).isInstanceOf(LectorEbay.class);
            assertThat(despachador.lectorDe(CodigoFuente.AMAZON)).isNull();
            assertThat(ctx.getBeansOfType(LectorFuenteEspecifico.class).values())
                    .noneMatch(LectorFuenteDespachador.class::isInstance)
                    .hasSize(1);

            ResultadoLectura ebay = despachador.buscar(CodigoFuente.EBAY, "https://ebay", "s", null, List.of());
            ResultadoLectura amazon = despachador.leerUrl(CodigoFuente.AMAZON, "https://am", "s", List.of());
            assertThat(ebay.tipo()).isEqualTo(ResultadoLectura.Tipo.SIN_RESULTADOS);
            assertThat(amazon.tipo()).isEqualTo(ResultadoLectura.Tipo.NO_CONECTADO);
        });
    }

    @Test
    void arrancaConVariosLectoresYResuelveCadaFuente() {
        base.withUserConfiguration(Karla.class, Mell.class, Ale.class).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBeansOfType(LectorFuente.class)).hasSize(1);
            LectorFuenteDespachador despachador = ctx.getBean(LectorFuenteDespachador.class);

            assertThat(despachador.lectorDe(CodigoFuente.MERCADO_LIBRE)).isInstanceOf(LectorKarla.class);
            assertThat(despachador.lectorDe(CodigoFuente.ALIBABA)).isInstanceOf(LectorKarla.class);
            assertThat(despachador.lectorDe(CodigoFuente.MERCADO_LIBRE))
                    .isSameAs(despachador.lectorDe(CodigoFuente.ALIBABA));
            assertThat(despachador.lectorDe(CodigoFuente.EBAY)).isInstanceOf(LectorMell.class);
            assertThat(despachador.lectorDe(CodigoFuente.AMAZON)).isInstanceOf(LectorAle.class);
            assertThat(despachador.lectorDe(CodigoFuente.ALIEXPRESS)).isInstanceOf(LectorAle.class);
            assertThat(ctx.getBeansOfType(LectorFuenteEspecifico.class).values())
                    .noneMatch(LectorFuenteDespachador.class::isInstance)
                    .hasSize(3);

            ResultadoLectura ml = despachador.buscar(CodigoFuente.MERCADO_LIBRE, "u", "s",
                    consulta(), List.of());
            assertThat(ml.mensaje()).isEqualTo("KARLA");
            ResultadoLectura ebay = despachador.leerUrl(CodigoFuente.EBAY, "u", "s", List.of());
            assertThat(ebay.mensaje()).isEqualTo("MELL");
        });
    }

    @Test
    void dosLectoresDelMismoCodigoImpidenArrancar() {
        base.withUserConfiguration(Mell.class, OtroEbay.class).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(texto(ctx.getStartupFailure())).contains("Dos lectores registrados para EBAY");
        });
    }

    @Test
    void sinElDespachadorSigueElRespaldoNoConectado() {
        new ApplicationContextRunner()
                .withUserConfiguration(LectorFuenteConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(LectorFuente.class)).isInstanceOf(LectorFuenteNoConectado.class);
                    assertThat(ctx).doesNotHaveBean(LectorFuenteDespachador.class);
                });
    }

    private static ConsultaBusqueda consulta() {
        return new ConsultaBusqueda(null, "1R-0750", null, null, null, null);
    }

    private static String texto(Throwable fallo) {
        StringBuilder sb = new StringBuilder();
        Throwable actual = fallo;
        while (actual != null) {
            if (actual.getMessage() != null) {
                sb.append(actual.getMessage()).append('\n');
            }
            actual = actual.getCause();
        }
        return sb.toString();
    }

    @Configuration
    static class ConsultaMinima {
        @Bean
        ConsultaFuenteService consultaFuenteService(LectorFuente lector) {
            return new ConsultaFuenteService(
                    mock(FuenteBusquedaConfigRepository.class),
                    mock(FuenteSelectorRepository.class),
                    mock(SesionFuenteService.class),
                    lector,
                    mock(PausaFuente.class),
                    mock(AvisoFuenteConError.class));
        }
    }

    @Configuration
    static class SoloEbay {
        @Bean
        LectorFuenteEspecifico mell() {
            return new LectorEbay();
        }
    }

    @Configuration
    static class Karla {
        @Bean
        LectorFuenteEspecifico karla() {
            return new LectorKarla();
        }
    }

    @Configuration
    static class Mell {
        @Bean
        LectorFuenteEspecifico mell() {
            return new LectorMell();
        }
    }

    @Configuration
    static class Ale {
        @Bean
        LectorFuenteEspecifico ale() {
            return new LectorAle();
        }
    }

    @Configuration
    static class OtroEbay {
        @Bean
        LectorFuenteEspecifico otroEbay() {
            return new LectorEbay();
        }
    }

    static final class LectorKarla extends LectorMarca {
        LectorKarla() {
            super("KARLA", Set.of(CodigoFuente.MERCADO_LIBRE, CodigoFuente.ALIBABA));
        }
    }

    static final class LectorMell extends LectorMarca {
        LectorMell() {
            super("MELL", Set.of(CodigoFuente.EBAY));
        }
    }

    static final class LectorAle extends LectorMarca {
        LectorAle() {
            super("ALE", Set.of(CodigoFuente.AMAZON, CodigoFuente.ALIEXPRESS));
        }
    }

    static final class LectorEbay extends LectorMarca {
        LectorEbay() {
            super("EBAY", Set.of(CodigoFuente.EBAY));
        }

        @Override
        public ResultadoLectura buscar(CodigoFuente codigo, String urlBase, String sesion,
                                       ConsultaBusqueda consulta, List<SelectorFuenteDto> selectores) {
            return ResultadoLectura.sinResultados();
        }
    }

    private abstract static class LectorMarca implements LectorFuenteEspecifico {
        private final String marca;
        private final Set<CodigoFuente> codigos;

        private LectorMarca(String marca, Set<CodigoFuente> codigos) {
            this.marca = marca;
            this.codigos = codigos;
        }

        @Override
        public Set<CodigoFuente> fuentes() {
            return codigos;
        }

        @Override
        public ResultadoLectura buscar(CodigoFuente codigo, String urlBase, String sesion,
                                       ConsultaBusqueda consulta, List<SelectorFuenteDto> selectores) {
            return ResultadoLectura.error(marca, false);
        }

        @Override
        public ResultadoLectura leerUrl(CodigoFuente codigo, String url, String sesion,
                                        List<SelectorFuenteDto> selectores) {
            return ResultadoLectura.error(marca, false);
        }
    }
}
