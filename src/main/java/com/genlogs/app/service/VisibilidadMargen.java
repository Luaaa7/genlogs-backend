package com.genlogs.app.service;

import java.math.BigDecimal;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import com.genlogs.app.dto.CotizacionResponse;
import com.genlogs.app.enums.RolSistema;

/**
 * Único lugar que decide si el JSON de una cotización lleva margen o precio de proveedor.
 * Lo llama {@link CotizacionService} al armar el DTO. Los controladores no repiten el criterio.
 */
public final class VisibilidadMargen {

    private VisibilidadMargen() {
    }

    public static boolean puedeVerMargen(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String nombre = sinPrefijo(authority.getAuthority());
            if (RolSistema.GERENTE_GENERAL.name().equals(nombre)
                    || RolSistema.JEFE_ADMINISTRATIVO.name().equals(nombre)
                    || RolSistema.ASISTENTE_LOGISTICO.name().equals(nombre)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Copia margen y precio de proveedor al DTO solo si el rol puede verlos.
     * Si no, los campos quedan null y {@code @JsonInclude(NON_NULL)} los omite del JSON.
     */
    public static void aplicar(
            CotizacionResponse.CotizacionResponseBuilder builder,
            BigDecimal margen,
            BigDecimal precioProveedor,
            Authentication authentication) {
        if (builder == null || !puedeVerMargen(authentication)) {
            return;
        }
        builder.margen(margen);
        builder.precioProveedor(precioProveedor);
    }

    private static String sinPrefijo(String authority) {
        if (authority != null && authority.startsWith("ROLE_")) {
            return authority.substring("ROLE_".length());
        }
        return authority;
    }
}
