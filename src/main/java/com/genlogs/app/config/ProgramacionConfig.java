package com.genlogs.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa el programador. Cada tarea decide si corre con su propiedad. */
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}
