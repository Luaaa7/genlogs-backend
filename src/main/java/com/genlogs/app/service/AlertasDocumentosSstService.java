package com.genlogs.app.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.genlogs.app.enums.EtapaDespacho;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.model.DespachoSst;
import com.genlogs.app.repository.AlertaSstRepository;

/**
 * Alertas de PASE y SCTR por vencer (BE-D): avisos en la campanita a los 30, 15 y 7 días.
 *
 * <ul>
 *   <li>Cada documento recibe un aviso por tramo: de 16 a 30 días (aviso de 30), de 8 a 15
 *       (aviso de 15) y de 0 a 7 (aviso de 7). Si la tarea no corrió el día exacto, el aviso
 *       sale el primer día en que corra dentro del tramo.</li>
 *   <li>No se repite: el título lleva tipo, despacho, fecha de vencimiento y umbral, y antes
 *       de avisar se revisa si ya existe. Si renuevan el documento con otra fecha, vuelve a avisar.</li>
 *   <li>Solo despachos que aún no se entregan. Sin correo (D-21: el correo es solo para alto
 *       valor y plazos vencidos).</li>
 *   <li>Un documento que falla no detiene a los demás.</li>
 * </ul>
 */
@Service
public class AlertasDocumentosSstService {

    private static final Logger log = LoggerFactory.getLogger(AlertasDocumentosSstService.class);

    /** Umbrales en días, de menor a mayor. */
    static final List<Integer> UMBRALES = List.of(7, 15, 30);
    /** Quién confirma PASE y SCTR y quién despacha. */
    static final List<RolSistema> DESTINATARIOS = List.of(RolSistema.SUPERVISOR_SST, RolSistema.ASISTENTE_LOGISTICO);
    /** El despacho cuelga de la orden de compra; es la pantalla interna que existe hoy. */
    static final String ENLACE = "/ordenes-compra";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AlertaSstRepository alertas;
    private final NotificacionService notificacionService;

    public AlertasDocumentosSstService(AlertaSstRepository alertas, NotificacionService notificacionService) {
        this.alertas = alertas;
        this.notificacionService = notificacionService;
    }

    /** Revisa los documentos que vencen en los próximos 30 días y avisa los que falten. */
    public Resultado avisar(LocalDate hoy) {
        int maximo = UMBRALES.get(UMBRALES.size() - 1);
        List<DespachoSst> documentos = alertas.porVencer(hoy, hoy.plusDays(maximo), EtapaDespacho.ENTREGADO);
        int enviados = 0;
        int repetidos = 0;
        int fallidos = 0;
        for (DespachoSst documento : documentos) {
            long dias = ChronoUnit.DAYS.between(hoy, documento.getFechaVencimiento());
            Integer umbral = umbralDe(dias);
            if (umbral == null) {
                continue;
            }
            String titulo = titulo(documento, umbral);
            try {
                if (alertas.contarAvisos(TipoAviso.DOCUMENTO_POR_VENCER.name(), titulo) > 0) {
                    repetidos++;
                    continue;
                }
                String mensaje = mensaje(documento, dias);
                for (RolSistema rol : DESTINATARIOS) {
                    notificacionService.avisarRol(rol, TipoAviso.DOCUMENTO_POR_VENCER, titulo, mensaje, ENLACE, false);
                }
                enviados++;
            } catch (RuntimeException e) {
                fallidos++;
                log.warn("No se pudo avisar \"{}\": {}", titulo, e.getMessage());
            }
        }
        Resultado resultado = new Resultado(documentos.size(), enviados, repetidos, fallidos);
        log.info("Alertas de PASE y SCTR del {}: {}", hoy, resultado);
        return resultado;
    }

    /** El menor umbral que cubre los días que faltan; null si faltan más de 30 o ya venció. */
    static Integer umbralDe(long dias) {
        if (dias < 0) {
            return null;
        }
        for (Integer umbral : UMBRALES) {
            if (dias <= umbral) {
                return umbral;
            }
        }
        return null;
    }

    static String titulo(DespachoSst documento, int umbral) {
        return documento.getTipo().name() + " del despacho #" + documento.getDespacho().getIdDespacho()
                + " vence el " + documento.getFechaVencimiento().format(FECHA)
                + " (aviso de " + umbral + " días)";
    }

    private static String mensaje(DespachoSst documento, long dias) {
        String cuando = dias == 0 ? "Vence hoy" : dias == 1 ? "Falta 1 día" : "Faltan " + dias + " días";
        String referencia = documento.getReferencia() == null || documento.getReferencia().isBlank()
                ? "" : " Referencia: " + documento.getReferencia().trim() + ".";
        return cuando + " para que venza el " + documento.getTipo().name() + " del despacho #"
                + documento.getDespacho().getIdDespacho() + "." + referencia
                + " Renuévalo antes de que bloquee el despacho.";
    }

    /** Cuántos documentos se revisaron y qué pasó con ellos. */
    public record Resultado(int revisados, int enviados, int repetidos, int fallidos) {
    }
}
