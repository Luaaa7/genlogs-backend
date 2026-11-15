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
 * La aplicación arranca sin lectores de tienda. {@code ConsultaFuenteService}
 * recibe el despachador, no el respaldo ni un lector específico.
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
        assertThat(contexto.getBeansOfType(LectorFuenteEspecifico.class)).isEmpty();
        assertThat(contexto.getBeansOfType(LectorFuente.class).values())
                .allMatch(LectorFuenteDespachador.class::isInstance);
    }
}
