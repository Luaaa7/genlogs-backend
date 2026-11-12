package com.genlogs.app.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A las 09:00 de Lima, recordatorio de facturas ya vencidas. */
@Component
public class FacturasVencidasTarea {

    private final FacturasVencidasService facturasVencidasService;

    public FacturasVencidasTarea(FacturasVencidasService facturasVencidasService) {
        this.facturasVencidasService = facturasVencidasService;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "America/Lima")
    public void diaria() {
        facturasVencidasService.avisarVencidas();
    }
}
