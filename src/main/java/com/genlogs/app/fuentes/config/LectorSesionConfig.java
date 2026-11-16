package com.genlogs.app.fuentes.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Si nadie registra un LectorSesion real (adaptador de P2), «Probar sesión» responde con un error claro. */
@Configuration
public class LectorSesionConfig {

    @Bean
    @ConditionalOnMissingBean(LectorSesion.class)
    LectorSesion lectorSesionNoConectado() {
        return new LectorSesionNoConectado();
    }
}
