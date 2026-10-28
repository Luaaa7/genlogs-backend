package com.genlogs.app.service.sunat;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

/**
 * Emisión SIMULADA: no se conecta a SUNAT ni a ningún proveedor, así que no
 * genera obligaciones tributarias para la empresa. Revisa lo mismo que SUNAT
 * revisaría primero (RUC del cliente en facturas, montos, ítems) para que los
 * errores aparezcan ya en las pruebas y no recién cuando se pase a real.
 */
@Component
public class EmisorSunatSimulado implements EmisorSunat {

    public static final String MODO = "SIMULADO";

    @Override
    public String modo() {
        return MODO;
    }

    @Override
    public ResultadoEmisionSunat emitir(ComprobanteSunat c) {
        if ("FACTURA".equalsIgnoreCase(c.tipoCodigo())) {
            String ruc = c.clienteNumeroDocumento();
            if (!"RUC".equalsIgnoreCase(c.clienteTipoDocumento()) || ruc == null || !ruc.matches("^(10|15|17|20)[0-9]{9}$")) {
                return ResultadoEmisionSunat.rechazado(
                        "Una factura necesita que el cliente tenga RUC válido (11 dígitos que empiezan con 10, 15, 17 o 20). "
                                + "Para personas con DNI emite una boleta.");
            }
        }
        if (c.lineas() == null || c.lineas().isEmpty()) {
            return ResultadoEmisionSunat.rechazado("El comprobante no tiene ítems.");
        }
        if (c.total() == null || c.total().compareTo(BigDecimal.ZERO) <= 0) {
            return ResultadoEmisionSunat.rechazado("El total del comprobante debe ser mayor que 0.");
        }
        return ResultadoEmisionSunat.aceptado(
                "La " + nombre(c.tipoCodigo()) + " " + c.codigo()
                        + " ha sido aceptada (SIMULACIÓN: no se envió a SUNAT y no tiene valor tributario).");
    }

    private static String nombre(String tipo) {
        return "BOLETA".equalsIgnoreCase(tipo) ? "boleta" : "factura";
    }
}