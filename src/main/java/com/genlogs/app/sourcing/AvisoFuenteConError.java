package com.genlogs.app.sourcing;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.repository.NotificacionRepository;
import com.genlogs.app.service.NotificacionService;

/**
 * v3.3 · L4 · BPMN 9-B (evento de error «sesión vencida / tienda bloquea»).
 *
 * Cuando una fuente de búsqueda pasa a CON_ERROR o REQUIERE_VERIFICACION, avisa al
 * Jefe Administrativo en la campanita, como máximo una vez al día por fuente.
 * Solo campanita, sin correo: D-21 deja el correo para alto valor y vencidos.
 *
 * Lo llama el servicio de sesión (L1) al cambiar el estado de la fuente. No guarda
 * nada propio: la marca de «ya avisado hoy» es la misma notificación.
 */
@Service
public class AvisoFuenteConError {

    static final ZoneId LIMA = ZoneId.of("America/Lima");
    static final String CON_ERROR = "CON_ERROR";
    static final String REQUIERE_VERIFICACION = "REQUIERE_VERIFICACION";
    private static final Set<String> ESTADOS_CON_AVISO = Set.of(CON_ERROR, REQUIERE_VERIFICACION);

    /** Pantalla Configuración > Fuentes de búsqueda (L2). Una fuente por enlace: sirve para no repetir. */
    static final String ENLACE_BASE = "/configuracion/fuentes?fuente=";

    private static final Logger log = LoggerFactory.getLogger(AvisoFuenteConError.class);

    private final NotificacionRepository notificacionRepository;
    private final NotificacionService notificacionService;
    private Clock clock = Clock.system(LIMA);

    public AvisoFuenteConError(NotificacionRepository notificacionRepository,
            NotificacionService notificacionService) {
        this.notificacionRepository = notificacionRepository;
        this.notificacionService = notificacionService;
    }

    /**
     * @param codigoFuente código de fuente_busqueda (EBAY, AMAZON, ...)
     * @param estado       estado nuevo de la fuente
     * @param detalle      último error, puede ser null
     * @return true si se creó el aviso
     */
    public boolean avisarSiCorresponde(String codigoFuente, String estado, String detalle) {
        if (codigoFuente == null || codigoFuente.isBlank() || estado == null
                || !ESTADOS_CON_AVISO.contains(estado)) {
            return false;
        }
        String codigo = codigoFuente.trim().toUpperCase(Locale.ROOT);
        String enlace = ENLACE_BASE + codigo;
        // Un fallo al avisar no debe cortar la búsqueda ni la prueba de sesión.
        try {
            if (yaAvisadaHoy(enlace)) {
                return false;
            }
            notificacionService.avisarRol(RolSistema.JEFE_ADMINISTRATIVO, TipoAviso.FUENTE_CON_ERROR,
                    titulo(codigo, estado), mensaje(codigo, estado, detalle), enlace, false);
            return true;
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar la fuente {} en estado {}: {}", codigo, estado, e.getMessage());
            return false;
        }
    }

    /** Solo para pruebas. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    private boolean yaAvisadaHoy(String enlace) {
        LocalDate hoy = LocalDate.now(clock);
        LocalDateTime desde = hoy.atStartOfDay();
        LocalDateTime hasta = hoy.plusDays(1).atStartOfDay();
        return notificacionRepository
                .existsByTipoAndEnlaceAndFechaCreacionGreaterThanEqualAndFechaCreacionLessThan(
                        TipoAviso.FUENTE_CON_ERROR.name(), enlace, desde, hasta);
    }

    static String titulo(String codigo, String estado) {
        return REQUIERE_VERIFICACION.equals(estado)
                ? "Fuente " + codigo + ": completar verificación"
                : "Fuente " + codigo + " con error";
    }

    static String mensaje(String codigo, String estado, String detalle) {
        String base = REQUIERE_VERIFICACION.equals(estado)
                ? "La tienda pide un código o CAPTCHA. Complétalo en Configuración > Fuentes de búsqueda."
                : "La búsqueda sigue con las demás fuentes. Revisa la cuenta en Configuración > Fuentes de búsqueda.";
        if (detalle == null || detalle.isBlank()) {
            return base;
        }
        // Nunca credenciales en el aviso: solo el texto del error, recortado.
        String limpio = detalle.strip();
        return base + " Último error: " + (limpio.length() > 200 ? limpio.substring(0, 200) + "…" : limpio);
    }
}
