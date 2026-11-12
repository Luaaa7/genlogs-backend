package com.genlogs.app.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.genlogs.app.enums.EtapaDespacho;

/**
 * Textos del bloqueo de salida. El frontend (FE-5) detecta el prefijo
 * {@code DESPACHO_BLOQUEADO_SST}. No se usa para rechazar una confirmación.
 */
public final class DespachoBloqueoSst {

    public static final String PREFIJO = "DESPACHO_BLOQUEADO_SST";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DespachoBloqueoSst() {
    }

    /**
     * Bloquear también ENTREGADO es una decisión pendiente del negocio
     * (si el SCTR vence en tránsito, no se podría cerrar la entrega).
     * Para cambiarlo, quita ENTREGADO de esta condición.
     */
    public static boolean exigeDocumento(EtapaDespacho destino) {
        return destino == EtapaDespacho.EN_TRANSITO || destino == EtapaDespacho.ENTREGADO;
    }

    /** Nulo si pase y SCTR están vigentes. El mismo día de vencimiento todavía vale. */
    public static String mensaje(LocalDate vencimientoPase, LocalDate vencimientoSctr, LocalDate hoy) {
        boolean faltaPase = vencimientoPase == null;
        boolean faltaSctr = vencimientoSctr == null;
        boolean paseVencido = !faltaPase && vencimientoPase.isBefore(hoy);
        boolean sctrVencido = !faltaSctr && vencimientoSctr.isBefore(hoy);
        boolean paseVigente = !faltaPase && !paseVencido;
        boolean sctrVigente = !faltaSctr && !sctrVencido;
        if (paseVigente && sctrVigente) {
            return null;
        }
        if (faltaPase && faltaSctr) {
            return PREFIJO + ": falta pase y SCTR vigente";
        }
        if (faltaPase && sctrVigente) {
            return PREFIJO + ": falta pase";
        }
        if (paseVigente && faltaSctr) {
            return PREFIJO + ": falta SCTR vigente";
        }
        if (paseVigente && sctrVencido) {
            return PREFIJO + ": SCTR vencido el " + FECHA.format(vencimientoSctr);
        }
        if (paseVencido && sctrVigente) {
            return PREFIJO + ": pase vencido el " + FECHA.format(vencimientoPase);
        }
        if (faltaPase && sctrVencido) {
            return PREFIJO + ": falta pase y SCTR vencido el " + FECHA.format(vencimientoSctr);
        }
        if (paseVencido && faltaSctr) {
            return PREFIJO + ": falta SCTR vigente y pase vencido el " + FECHA.format(vencimientoPase);
        }
        return PREFIJO + ": pase vencido el " + FECHA.format(vencimientoPase)
                + " y SCTR vencido el " + FECHA.format(vencimientoSctr);
    }
}
