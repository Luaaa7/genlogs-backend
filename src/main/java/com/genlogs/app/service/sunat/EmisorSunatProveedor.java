package com.genlogs.app.service.sunat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.genlogs.app.exception.BusinessException;

/**
 * Emisión REAL a través de un proveedor autorizado por SUNAT (OSE/PSE, por
 * ejemplo Nubefact). Se activa con SUNAT_MODO=PROVEEDOR.
 *
 * <p>PENDIENTE: cuando la empresa elija proveedor, aquí se arma el JSON que pide
 * su API con los datos de {@link ComprobanteSunat}, se envía con el token
 * (SUNAT_PROVEEDOR_TOKEN) a SUNAT_PROVEEDOR_URL y se convierte su respuesta en
 * {@link ResultadoEmisionSunat}. Nada más del sistema tiene que cambiar.</p>
 *
 * <p>Mientras no esté hecho, en vez de inventar una respuesta se detiene con un
 * mensaje claro, para que nadie crea que una factura se emitió de verdad.</p>
 */
@Component
public class EmisorSunatProveedor implements EmisorSunat {

    public static final String MODO = "PROVEEDOR";

    @Value("${app.sunat.proveedor-url:}")
    private String url;

    @Value("${app.sunat.proveedor-token:}")
    private String token;

    @Override
    public String modo() {
        return MODO;
    }

    @Override
    public ResultadoEmisionSunat emitir(ComprobanteSunat comprobante) {
        if (url == null || url.isBlank() || token == null || token.isBlank()) {
            throw new BusinessException(
                    "Falta configurar el proveedor de facturación electrónica (SUNAT_PROVEEDOR_URL y SUNAT_PROVEEDOR_TOKEN).");
        }
        throw new BusinessException(
                "La conexión con el proveedor de facturación electrónica todavía no está programada. "
                        + "Usa SUNAT_MODO=SIMULADO mientras tanto.");
    }
}