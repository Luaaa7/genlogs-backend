package com.genlogs.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * Datos del emisor (GENLOGS) que se imprimen en los PDF de cotizaciones y facturas.
 * Se configuran en application.properties (genlogs.emisor.*) o con las variables
 * EMISOR_RAZON_SOCIAL, EMISOR_RUC y EMISOR_DIRECCION.
 */
@Component
@Getter
public class EmisorProperties {

    private final String razonSocial;
    private final String ruc;
    private final String direccion;

    public EmisorProperties(
            @Value("${genlogs.emisor.razon-social:General Logistic Solutions}") String razonSocial,
            @Value("${genlogs.emisor.ruc:20516185644}") String ruc,
            @Value("${genlogs.emisor.direccion:}") String direccion) {
        this.razonSocial = razonSocial;
        this.ruc = ruc;
        this.direccion = direccion;
    }
}
