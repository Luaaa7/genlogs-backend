package com.genlogs.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Corre todos los días a las 08:30 de Lima. Al arrancar solo si
 * genlogs.alertas-sst.al-arrancar=true. activo=false no registra este bean.
 * Mismo patrón que VencimientoCotizacionesTarea.
 */
@Component
@ConditionalOnProperty(name = "genlogs.alertas-sst.activo", havingValue = "true", matchIfMissing = true)
public class AlertasDocumentosSstTarea {

    private static final Logger log = LoggerFactory.getLogger(AlertasDocumentosSstTarea.class);

    private final AlertasDocumentosSstService alertasDocumentosSstService;
    private final boolean alArrancar;

    public AlertasDocumentosSstTarea(AlertasDocumentosSstService alertasDocumentosSstService,
            @Value("${genlogs.alertas-sst.al-arrancar:false}") boolean alArrancar) {
        this.alertasDocumentosSstService = alertasDocumentosSstService;
        this.alArrancar = alArrancar;
    }

    @Scheduled(cron = "0 30 8 * * *", zone = "America/Lima")
    public void diaria() {
        correr();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        if (alArrancar) {
            correr();
        }
    }

    void correr() {
        try {
            alertasDocumentosSstService.avisar();
        } catch (RuntimeException ex) {
            log.error("No se pudieron revisar los PASE y SCTR por vencer", ex);
        }
    }
}
