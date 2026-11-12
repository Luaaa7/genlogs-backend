package com.genlogs.app.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.model.EstadoFacturacion;
import com.genlogs.app.model.Facturacion;
import com.genlogs.app.repository.FacturacionRepository;
import com.genlogs.app.repository.NotificacionRepository;

/**
 * Recordatorio diario de facturas vencidas (RF de cobranza). No cambia el estado:
 * EMITIDA y PARCIAL siguen igual. PAGADA y ANULADA no se avisan.
 */
@Service
public class FacturasVencidasService {

    static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final List<String> ABIERTAS = List.of("EMITIDA", "PARCIAL");
    private static final String TITULO = "Factura vencida";

    private final FacturacionRepository facturacionRepository;
    private final NotificacionRepository notificacionRepository;
    private final NotificacionService notificacionService;
    private Clock clock = Clock.system(LIMA);

    public FacturasVencidasService(FacturacionRepository facturacionRepository,
            NotificacionRepository notificacionRepository,
            NotificacionService notificacionService) {
        this.facturacionRepository = facturacionRepository;
        this.notificacionRepository = notificacionRepository;
        this.notificacionService = notificacionService;
    }

    /** La llama la tarea de las 09:00. Sin destinatarios, avisarRol solo registra el aviso vacío. */
    public void avisarVencidas() {
        LocalDate hoy = LocalDate.now(clock);
        for (Facturacion factura : facturacionRepository.vencidas(hoy, ABIERTAS)) {
            if (!estaVencida(factura, hoy) || yaAvisada(factura, hoy)) {
                continue;
            }
            notificacionService.avisarRol(RolSistema.CONTADOR_GENERAL, TipoAviso.FACTURA_VENCIDA, TITULO,
                    mensaje(factura), enlace(factura), true);
        }
    }

    /** Solo para pruebas. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    private boolean yaAvisada(Facturacion factura, LocalDate hoy) {
        LocalDateTime desde = hoy.atStartOfDay();
        LocalDateTime hasta = hoy.plusDays(1).atStartOfDay();
        return notificacionRepository
                .existsByTipoAndEnlaceAndFechaCreacionGreaterThanEqualAndFechaCreacionLessThan(
                        TipoAviso.FACTURA_VENCIDA.name(), enlace(factura), desde, hasta);
    }

    static boolean estaVencida(Facturacion factura, LocalDate hoy) {
        if (factura.getFechaVencimiento() == null || !factura.getFechaVencimiento().isBefore(hoy)) {
            return false;
        }
        EstadoFacturacion estado = factura.getEstadoFacturacion();
        String codigo = estado == null ? null : estado.getCodigoEstado();
        return "EMITIDA".equals(codigo) || "PARCIAL".equals(codigo);
    }

    static String mensaje(Facturacion factura) {
        return factura.getSerieComprobante() + "-" + factura.getNumeroComprobante()
                + " venció el " + factura.getFechaVencimiento();
    }

    private static String enlace(Facturacion factura) {
        return "/facturacion/" + factura.getIdFacturacion();
    }
}
