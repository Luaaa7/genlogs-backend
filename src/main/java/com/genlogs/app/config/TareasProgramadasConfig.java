package com.genlogs.app.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas de cada bloque (tipo de cambio de BE-D, alertas de plazo
 * de BE-B, vencimiento de cotizaciones de BE-C). Se apagan todas con
 * genlogs.tareas.habilitadas=false; las pruebas lo hacen para que nada corra solo.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "genlogs.tareas.habilitadas", havingValue = "true", matchIfMissing = true)
public class TareasProgramadasConfig {
}
