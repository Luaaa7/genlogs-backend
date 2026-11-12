package com.genlogs.app.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A las 08:00 de Lima, cuando el tipo de cambio del día ya está publicado. */
@Component
public class ConsultaTipoCambioTarea {

    private final ConsultaTipoCambioService consultaTipoCambioService;

    public ConsultaTipoCambioTarea(ConsultaTipoCambioService consultaTipoCambioService) {
        this.consultaTipoCambioService = consultaTipoCambioService;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Lima")
    public void diaria() {
        consultaTipoCambioService.consultarHoy();
    }
}
