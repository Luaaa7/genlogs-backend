package com.genlogs.app.vitrina;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Tarea diaria de verificación de la vitrina (RF-83). Solo existe con la vitrina
 * encendida. Hora configurable: genlogs.vitrina.verificacion.cron (por defecto 6:10, hora de Lima).
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "genlogs.vitrina.habilitada", havingValue = "true")
public class VerificacionVitrinaTarea {

    private final VerificacionVitrinaService verificacionVitrinaService;

    @Scheduled(cron = "${genlogs.vitrina.verificacion.cron:0 10 6 * * *}", zone = "America/Lima")
    public void verificar() {
        verificacionVitrinaService.verificarPublicados();
    }
}
