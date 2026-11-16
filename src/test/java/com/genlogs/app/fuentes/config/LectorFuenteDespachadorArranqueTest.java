package com.genlogs.app.fuentes.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.genlogs.app.sourcing.ConsultaFuenteService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La aplicación arranca con los lectores de tienda que existan (eBay, y luego los de
 * Karla y Ale). {@code ConsultaFuenteService} recibe el despachador, no el respaldo
 * ni un lector específico, y ningún lector específico es a la vez un {@link LectorFuente}.
 */
@SpringBootTest
@ActiveProfiles("test")
class LectorFuenteDespachadorArranqueTest {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private ConsultaFuenteService consultaFuenteService;

    @Autowired
    private LectorFuente lector;

    @Test
    void elContextoArrancaYElServicioRecibeElDespachador() {
        assertThat(lector).isInstanceOf(LectorFuenteDespachador.class);
        assertThat(ReflectionTestUtils.getField(consultaFuenteService, "lector")).isSameAs(lector);
        assertThat(contexto.getBeansOfType(LectorFuenteEspecifico.class).values())
                .noneMatch(LectorFuente.class::isInstance);
        assertThat(contexto.getBeansOfType(LectorFuente.class).values())
                .allMatch(LectorFuenteDespachador.class::isInstance);
    }
}