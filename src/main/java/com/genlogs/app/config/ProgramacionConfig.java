package com.genlogs.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa las tareas diarias: tipo de cambio a las 08:00 y facturas vencidas a las 09:00, hora de Lima. */
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}
