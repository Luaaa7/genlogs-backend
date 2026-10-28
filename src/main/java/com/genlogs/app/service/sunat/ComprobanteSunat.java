package com.genlogs.app.service.sunat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Datos del comprobante tal como se le mandan al emisor (simulado o proveedor real).
 * Tiene lo que pide SUNAT para una factura o boleta, así la versión real del emisor
 * no necesita leer la base de datos.
 */
public record ComprobanteSunat(
        String tipoCodigo,            // FACTURA / BOLETA
        String serie,                 // F001
        String numero,                // 00000008
        LocalDate fechaEmision,
        LocalDate fechaVencimiento,
        String rucEmisor,
        String clienteTipoDocumento,  // RUC / DNI / CE / PAS
        String clienteNumeroDocumento,
        String clienteRazonSocial,
        String clienteDireccion,
        String clienteCorreo,
        String moneda,                // PEN / USD
        String condicionPago,
        BigDecimal porcentajeIgv,
        BigDecimal subtotal,
        BigDecimal igv,
        BigDecimal total,
        List<Linea> lineas) {

    public record Linea(
            String descripcion,
            String unidad,            // NIU (unidad) o ZZ (servicio)
            BigDecimal cantidad,
            BigDecimal valorUnitario, // sin IGV
            BigDecimal importe) {     // cantidad x valor, sin IGV
    }

    public String codigo() {
        return serie + "-" + numero;
    }
}