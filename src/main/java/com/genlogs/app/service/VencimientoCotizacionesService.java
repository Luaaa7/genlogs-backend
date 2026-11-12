package com.genlogs.app.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.SeguimientoCotizacion;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.EstadoCotizacionRepository;
import com.genlogs.app.repository.SeguimientoCotizacionRepository;

import jakarta.persistence.EntityManager;

/**
 * Vence cotizaciones enviadas o en negociación con más de 15 días desde fechaCotizacion.
 * Se modifican las entidades ya bloqueadas, no un UPDATE masivo: la cotización no tiene
 * @Version, y el seguimiento necesita el estado que tenía cada fila. Un fallo revierte
 * todo el lote porque esto corre en una sola transacción.
 */
@Service
public class VencimientoCotizacionesService {

    static final String USUARIO = "SISTEMA";
    static final String PROCESO = "VENCIMIENTO_COTIZACION";
    static final String COMENTARIO = "Vencida automáticamente por pasar de 15 días";
    static final String MENSAJE_AVISO = " cotizaciones vencieron automáticamente";
    static final int MAXIMO_POR_DEFECTO = 500;
    static final int MUESTRA_LOG = 50;

    private static final Logger log = LoggerFactory.getLogger(VencimientoCotizacionesService.class);
    private static final Set<String> ABIERTAS = Set.of("ENVIADA", "EN_NEGOCIACION");
    private static final int LOTE = 50;
    private static final String TITULO_AVISO = "Cotizaciones vencidas";
    private static final String ENLACE_AVISO = "/cotizaciones";

    private final CotizacionRepository cotizacionRepository;
    private final EstadoCotizacionRepository estadoCotizacionRepository;
    private final SeguimientoCotizacionRepository seguimientoCotizacionRepository;
    private final EntityManager entityManager;
    private final NotificacionService notificacionService;
    private final boolean simulacion;
    private final int maximoPorCorrida;

    public VencimientoCotizacionesService(CotizacionRepository cotizacionRepository,
            EstadoCotizacionRepository estadoCotizacionRepository,
            SeguimientoCotizacionRepository seguimientoCotizacionRepository,
            EntityManager entityManager,
            NotificacionService notificacionService,
            @Value("${genlogs.vencimiento.simulacion:false}") boolean simulacion,
            @Value("${genlogs.vencimiento.maximo-por-corrida:500}") int maximoPorCorrida) {
        this.cotizacionRepository = cotizacionRepository;
        this.estadoCotizacionRepository = estadoCotizacionRepository;
        this.seguimientoCotizacionRepository = seguimientoCotizacionRepository;
        this.entityManager = entityManager;
        this.notificacionService = notificacionService;
        this.simulacion = simulacion;
        this.maximoPorCorrida = maximoPorCorrida > 0 ? maximoPorCorrida : MAXIMO_POR_DEFECTO;
    }

    /**
     * En simulación solo cuenta la corrida y la lista en el log. No bloquea ni escribe.
     * El aviso no sale de aquí: lo llama la tarea después de que esta transacción confirma.
     */
    @Transactional
    public int vencer(LocalDate hoy) {
        if (hoy == null) {
            throw new BusinessException("Indica la fecha de hoy");
        }
        LocalDate limite = hoy.minusDays(15);
        List<CandidataVencimiento> candidatas = cotizacionRepository.candidatasPorVencer(limite, ABIERTAS);
        if (candidatas.isEmpty()) {
            log.info("Cotizaciones vencidas: 0");
            return 0;
        }
        int tope = Math.min(candidatas.size(), maximoPorCorrida);
        List<CandidataVencimiento> estaCorrida = candidatas.subList(0, tope);
        int pendientes = candidatas.size() - tope;
        if (simulacion) {
            log.info("Simulación de vencimiento: {} cotizaciones se vencerían y no se modificó nada. {}",
                    estaCorrida.size(), muestra(estaCorrida));
            avisarPendientes(pendientes);
            return estaCorrida.size();
        }
        EstadoCotizacion vencida = estadoCotizacionRepository.findByCodigoEstado("VENCIDA")
                .orElseThrow(() -> new BusinessException("No existe el estado VENCIDA configurado"));
        LocalDateTime ahora = LocalDateTime.now(PlazoDespacho.LIMA);
        int total = 0;
        int enLote = 0;
        for (CandidataVencimiento candidata : estaCorrida) {
            Cotizacion cotizacion = cotizacionRepository.bloquearPorId(candidata.idCotizacion()).orElse(null);
            if (cotizacion == null || !sigueAbierta(cotizacion, limite)) {
                continue;
            }
            cotizacion.setEstadoCotizacion(vencida);
            cotizacion.setUserUpdate(USUARIO);
            cotizacion.setProcessUpdate(PROCESO);
            cotizacion.setDateUpdate(ahora);
            seguimiento(cotizacion, vencida, ahora);
            total++;
            enLote++;
            if (enLote == LOTE) {
                entityManager.flush();
                enLote = 0;
            }
        }
        entityManager.flush();
        avisarPendientes(pendientes);
        log.info("Cotizaciones vencidas: {}", total);
        return total;
    }

    /**
     * Un aviso por rol, después del commit. Con 0 vencidas o en simulación no envía nada.
     * Si un rol falla, intenta el otro y luego relanza para que la tarea lo registre.
     */
    public void avisarAdministracion(int vencidas) {
        if (simulacion || vencidas <= 0) {
            return;
        }
        String mensaje = vencidas + MENSAJE_AVISO;
        RuntimeException fallo = null;
        fallo = intentar(fallo, RolSistema.ASISTENTE_LOGISTICO, mensaje);
        fallo = intentar(fallo, RolSistema.JEFE_ADMINISTRATIVO, mensaje);
        if (fallo != null) {
            throw fallo;
        }
    }

    /** Otra instancia pudo cambiarla mientras esta esperaba el bloqueo. */
    private static boolean sigueAbierta(Cotizacion cotizacion, LocalDate limite) {
        String codigo = cotizacion.getEstadoCotizacion() == null
                ? null
                : cotizacion.getEstadoCotizacion().getCodigoEstado();
        return codigo != null
                && ABIERTAS.contains(codigo)
                && cotizacion.getFechaCotizacion() != null
                && cotizacion.getFechaCotizacion().isBefore(limite);
    }

    private void seguimiento(Cotizacion cotizacion, EstadoCotizacion vencida, LocalDateTime ahora) {
        SeguimientoCotizacion seguimiento = new SeguimientoCotizacion();
        seguimiento.setCotizacion(cotizacion);
        seguimiento.setEstadoCotizacion(vencida);
        seguimiento.setFechaEvento(ahora);
        seguimiento.setComentario(COMENTARIO);
        seguimiento.setUserCreate(USUARIO);
        seguimiento.setProcessCreate(PROCESO);
        seguimiento.setDateCreate(ahora);
        seguimiento.setStatus("A");
        seguimientoCotizacionRepository.save(seguimiento);
    }

    private RuntimeException intentar(RuntimeException previo, RolSistema rol, String mensaje) {
        try {
            notificacionService.avisarRol(rol, TipoAviso.COTIZACION_VENCIDA, TITULO_AVISO, mensaje, ENLACE_AVISO,
                    false);
            return previo;
        } catch (RuntimeException e) {
            log.error("No se pudo avisar el vencimiento de cotizaciones a {}", rol, e);
            return previo != null ? previo : e;
        }
    }

    private static void avisarPendientes(int pendientes) {
        if (pendientes > 0) {
            log.warn("Quedan {} cotizaciones pendientes de vencer; la siguiente corrida las toma", pendientes);
        }
    }

    private static String muestra(List<CandidataVencimiento> candidatas) {
        int visibles = Math.min(candidatas.size(), MUESTRA_LOG);
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < visibles; i++) {
            CandidataVencimiento candidata = candidatas.get(i);
            if (i > 0) {
                texto.append("; ");
            }
            String codigo = candidata.codigoCotizacion();
            texto.append(codigo == null || codigo.isBlank() ? "id " + candidata.idCotizacion() : codigo.trim());
            texto.append(' ').append(candidata.fechaCotizacion());
        }
        int resto = candidatas.size() - visibles;
        if (resto > 0) {
            texto.append(" (+").append(resto).append(" más)");
        }
        return texto.toString();
    }
}
