package com.genlogs.app.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;

/**
 * BPMN 10-F (v3.4) · Factiliza rechaza la clave: 401/403 (token vencido o inválido),
 * 402 (sin plan) o 429 (sin consultas). Avisa al Jefe Administrativo con campanita y
 * correo, una sola vez al día, con la causa y qué hacer. Sin Factiliza no se valida el
 * RUC de empresas nuevas ni se actualiza el tipo de cambio.
 * Los demás errores (404, 5xx, sin red) no son del plan y no avisan aquí.
 */
@Component
public class AvisoFactiliza {

    private static final Logger log = LoggerFactory.getLogger(AvisoFactiliza.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Set<Integer> DEL_PLAN = Set.of(401, 402, 403, 429);
    static final String TITULO = "Factiliza rechazó la clave: plan vencido o sin consultas";
    static final String MENSAJE = "Factiliza dejó de responder con la clave actual: el plan venció, se acabaron las "
            + "consultas o el token ya no es válido. Mientras tanto no se valida el RUC de empresas nuevas en el "
            + "portal ni se actualiza el tipo de cambio (regístralo a mano). Renueva el plan en Factiliza o cambia "
            + "FACTILIZA_API_KEY en Render con un token nuevo.";

    private final NotificacionService notificacionService;
    private final Clock reloj;
    private final AtomicReference<LocalDate> ultimoAviso = new AtomicReference<>();

    @Autowired
    public AvisoFactiliza(NotificacionService notificacionService) {
        this(notificacionService, Clock.system(LIMA));
    }

    AvisoFactiliza(NotificacionService notificacionService, Clock reloj) {
        this.notificacionService = notificacionService;
        this.reloj = reloj;
    }

    LocalDate hoy() {
        return LocalDate.now(reloj.withZone(LIMA));
    }

    /** Recibe el código HTTP que respondió Factiliza. Nunca lanza excepción. */
    public void revisar(int codigoHttp) {
        if (!DEL_PLAN.contains(codigoHttp)) {
            return;
        }
        LocalDate dia = hoy();
        LocalDate anterior = ultimoAviso.getAndSet(dia);
        if (dia.equals(anterior)) {
            return;
        }
        log.error("Factiliza rechazó la clave (HTTP {}): plan vencido, sin consultas o token inválido", codigoHttp);
        try {
            notificacionService.avisarRol(RolSistema.JEFE_ADMINISTRATIVO, TipoAviso.FACTILIZA_SIN_PLAN,
                    TITULO, MENSAJE, "/tipo-cambio", true);
        } catch (RuntimeException e) {
            log.error("No se pudo avisar que Factiliza rechazó la clave", e);
        }
    }
}
