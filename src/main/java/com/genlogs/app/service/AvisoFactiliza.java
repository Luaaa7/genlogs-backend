package com.genlogs.app.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.repository.NotificacionRepository;

/**
 * BPMN 10-F (v3.4) · Factiliza rechaza la clave: 401/403 (token vencido o inválido) o 402
 * (sin plan). Avisa al Jefe Administrativo con campanita y correo, una sola vez por día de
 * Lima, con la causa y qué hacer. Se revisa en la tabla notificacion (no en memoria) para no
 * repetir el aviso cuando Render reinicia el backend.
 * El 429 es el límite de consultas por minuto: se recupera solo y no avisa. Los demás errores
 * (404, 5xx, sin red) no son del plan. El aviso nunca muestra la clave.
 */
@Component
public class AvisoFactiliza {

    private static final Logger log = LoggerFactory.getLogger(AvisoFactiliza.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Set<Integer> DEL_PLAN = Set.of(401, 402, 403);
    static final String ENLACE = "/configuracion/tipo-cambio";
    static final String TITULO = "Factiliza rechazó la clave: plan vencido o token inválido";
    static final String MENSAJE = "Factiliza dejó de responder con la clave actual: el plan venció o el token ya no "
            + "es válido. Mientras tanto el portal pide escribir la razón social a mano y se usa el último tipo de "
            + "cambio guardado. Registra el tipo de cambio de hoy a mano y avisa al equipo técnico para que "
            + "renueve el plan o cambie la clave.";

    private final NotificacionService notificacionService;
    private final NotificacionRepository notificacionRepository;
    private final Clock reloj;

    @Autowired
    public AvisoFactiliza(NotificacionService notificacionService, NotificacionRepository notificacionRepository) {
        this(notificacionService, notificacionRepository, Clock.system(LIMA));
    }

    AvisoFactiliza(NotificacionService notificacionService, NotificacionRepository notificacionRepository, Clock reloj) {
        this.notificacionService = notificacionService;
        this.notificacionRepository = notificacionRepository;
        this.reloj = reloj;
    }

    /** ¿Ese código HTTP de Factiliza significa plan vencido o clave inválida? */
    public static boolean esDelPlan(int codigoHttp) {
        return DEL_PLAN.contains(codigoHttp);
    }

    /** Recibe el código HTTP que respondió Factiliza. Nunca lanza excepción. */
    public void revisar(int codigoHttp) {
        if (!esDelPlan(codigoHttp)) {
            return;
        }
        log.error("Factiliza rechazó la clave (HTTP {}): plan vencido o token inválido", codigoHttp);
        try {
            if (yaSeAvisoHoy()) {
                return;
            }
            notificacionService.avisarRol(RolSistema.JEFE_ADMINISTRATIVO, TipoAviso.FACTILIZA_SIN_PLAN,
                    TITULO, MENSAJE, ENLACE, true);
        } catch (RuntimeException e) {
            log.error("No se pudo avisar que Factiliza rechazó la clave", e);
        }
    }

    private boolean yaSeAvisoHoy() {
        LocalDate hoy = LocalDate.now(reloj.withZone(LIMA));
        return notificacionRepository.existsByTipoAndEnlaceAndFechaCreacionGreaterThanEqualAndFechaCreacionLessThan(
                TipoAviso.FACTILIZA_SIN_PLAN.name(), ENLACE, hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
    }
}
