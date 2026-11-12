package com.genlogs.app.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Corre a la 01:00 de Lima. Al arrancar solo si genlogs.vencimiento.al-arrancar=true,
 * por si el servidor estaba apagado a esa hora. activo=false no registra este bean.
 * El aviso sale después de que vencer() confirma su transacción.
 */
@Component
@ConditionalOnProperty(name = "genlogs.vencimiento.activo", havingValue = "true", matchIfMissing = true)
public class VencimientoCotizacionesTarea {

    private static final Logger log = LoggerFactory.getLogger(VencimientoCotizacionesTarea.class);

    private final VencimientoCotizacionesService vencimientoCotizacionesService;
    private final boolean alArrancar;

    public VencimientoCotizacionesTarea(VencimientoCotizacionesService vencimientoCotizacionesService,
            @Value("${genlogs.vencimiento.al-arrancar:false}") boolean alArrancar) {
        this.vencimientoCotizacionesService = vencimientoCotizacionesService;
        this.alArrancar = alArrancar;
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "America/Lima")
    public void diaria() {
        correr();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        if (!alArrancar) {
            log.info("Vencimiento al arrancar desactivado");
            return;
        }
        correr();
    }

    private void correr() {
        int vencidas;
        try {
            vencidas = vencimientoCotizacionesService.vencer(LocalDate.now(PlazoDespacho.LIMA));
        } catch (RuntimeException ex) {
            log.error("No se pudieron vencer las cotizaciones", ex);
            return;
        }
        try {
            vencimientoCotizacionesService.avisarAdministracion(vencidas);
        } catch (RuntimeException ex) {
            log.error("No se pudo avisar que vencieron las cotizaciones", ex);
        }
    }
}
