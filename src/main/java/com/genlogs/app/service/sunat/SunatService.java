package com.genlogs.app.service.sunat;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.genlogs.app.exception.BusinessException;

/**
 * Elige el emisor según SUNAT_MODO (por defecto SIMULADO) y lo usa para emitir.
 * Pasar de simulación a real = cambiar variables en Render, no el código.
 */
@Service
public class SunatService {

    private final List<EmisorSunat> emisores;

    @Value("${app.sunat.modo:SIMULADO}")
    private String modo;

    @Value("${app.sunat.serie-factura:F001}")
    private String serieFactura;

    @Value("${app.sunat.serie-boleta:B001}")
    private String serieBoleta;

    @Value("${app.sunat.ruc-emisor:}")
    private String rucEmisor;

    public SunatService(List<EmisorSunat> emisores) {
        this.emisores = emisores;
    }

    public String modo() {
        return modo == null || modo.isBlank() ? EmisorSunatSimulado.MODO : modo.trim().toUpperCase(Locale.ROOT);
    }

    public boolean esSimulado() {
        return EmisorSunatSimulado.MODO.equals(modo());
    }

    /** Serie que se usa para cada tipo (configurable: SUNAT_SERIE_FACTURA / SUNAT_SERIE_BOLETA). */
    public String serie(String tipoCodigo, String seriePrefijo) {
        if ("FACTURA".equalsIgnoreCase(tipoCodigo)) return serieFactura;
        if ("BOLETA".equalsIgnoreCase(tipoCodigo)) return serieBoleta;
        return (seriePrefijo != null ? seriePrefijo : "F") + "001";
    }

    public String rucEmisor() {
        return rucEmisor;
    }

    public ResultadoEmisionSunat emitir(ComprobanteSunat comprobante) {
        String m = modo();
        EmisorSunat emisor = emisores.stream()
                .filter(e -> e.modo().equalsIgnoreCase(m))
                .findFirst()
                .orElseThrow(() -> new BusinessException("SUNAT_MODO no válido: " + m + " (usa SIMULADO o PROVEEDOR)."));
        return emisor.emitir(comprobante);
    }
}