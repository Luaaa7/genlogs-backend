package com.genlogs.app.fuentes.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Si nadie registra un LectorFuente real (adaptador de P2), la consulta responde con un error claro. */
@Configuration
public class LectorFuenteConfig {

    @Bean
    @ConditionalOnMissingBean(LectorFuente.class)
    LectorFuente lectorFuenteNoConectado() {
        return new LectorFuenteNoConectado();
    }
}
