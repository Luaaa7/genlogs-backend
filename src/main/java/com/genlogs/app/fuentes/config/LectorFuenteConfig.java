package com.genlogs.app.fuentes.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Respaldo solo si el despachador no está en el contexto (pruebas acotadas).
 * Con la aplicación completa, {@link LectorFuenteDespachador} es el único
 * {@link LectorFuente} y responde {@code NO_CONECTADO} por cada tienda que
 * todavía no tenga {@link LectorFuenteEspecifico}.
 */
@Configuration
public class LectorFuenteConfig {

    @Bean
    @ConditionalOnMissingBean(LectorFuente.class)
    LectorFuente lectorFuenteNoConectado() {
        return new LectorFuenteNoConectado();
    }
}
