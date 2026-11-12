package com.genlogs.app.fuentes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.genlogs.app.sourcing.FuenteBusqueda;

/**
 * Caso clave: sin llaves de eBay la aplicación arranca y no hay fuentes (la búsqueda
 * sigue con la captura por URL); con las dos llaves, eBay aparece como FuenteBusqueda.
 */
class FuentesConfiguracionTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ClienteHttpJdk.class, EbayFuente.class);

    @Test
    void sinLlavesLaAppArrancaSinFuentes() {
        contexto.withPropertyValues(
                        "genlogs.fuentes.ebay.client-id=",
                        "genlogs.fuentes.ebay.client-secret=")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(FuenteBusqueda.class);
                });
    }

    @Test
    void sinPropiedadesTampocoFalla() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(EbayFuente.class);
        });
    }

    @Test
    void unaSolaLlaveNoAlcanza() {
        contexto.withPropertyValues("genlogs.fuentes.ebay.client-id=app",
                        "genlogs.fuentes.ebay.client-secret=   ")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(EbayFuente.class));
    }

    @Test
    void conLasDosLlavesEbayEsLaUnicaFuente() {
        contexto.withPropertyValues(
                        "genlogs.fuentes.ebay.client-id=app",
                        "genlogs.fuentes.ebay.client-secret=secreto")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(EbayFuente.class);
                    assertThat(ctx.getBeansOfType(FuenteBusqueda.class)).hasSize(1);
                });
    }
}
