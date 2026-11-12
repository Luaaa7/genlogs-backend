package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.genlogs.app.dto.CotizacionResponse;
import com.genlogs.app.enums.RolSistema;

class VisibilidadMargenTest {

    private static final BigDecimal MARGEN = new BigDecimal("18.00");
    private static final BigDecimal PRECIO = new BigDecimal("40.00");

    @ParameterizedTest
    @EnumSource(value = RolSistema.class, names = {
            "GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO"
    })
    void esosRolesRecibenMargenYPrecioDeProveedor(RolSistema rol) {
        CotizacionResponse respuesta = armar(rol);

        assertTrue(VisibilidadMargen.puedeVerMargen(auth(rol)));
        assertEquals(0, MARGEN.compareTo(respuesta.getMargen()));
        assertEquals(0, PRECIO.compareTo(respuesta.getPrecioProveedor()));
    }

    @ParameterizedTest
    @EnumSource(value = RolSistema.class, names = {
            "ASISTENTE_ADMINISTRATIVO", "CLIENTE", "CONTADOR_GENERAL", "SUPERVISOR_SST"
    })
    void elRestoNoRecibeMargenNiPrecioDeProveedor(RolSistema rol) {
        CotizacionResponse respuesta = armar(rol);

        assertFalse(VisibilidadMargen.puedeVerMargen(auth(rol)));
        assertNull(respuesta.getMargen());
        assertNull(respuesta.getPrecioProveedor());
    }

    @Test
    void sinAutenticacionNoVeMargen() {
        assertFalse(VisibilidadMargen.puedeVerMargen(null));
    }

    private static CotizacionResponse armar(RolSistema rol) {
        CotizacionResponse.CotizacionResponseBuilder builder = CotizacionResponse.builder().idCotizacion(1L);
        VisibilidadMargen.aplicar(builder, MARGEN, PRECIO, auth(rol));
        return builder.build();
    }

    private static UsernamePasswordAuthenticationToken auth(RolSistema rol) {
        return new UsernamePasswordAuthenticationToken(
                "usuario",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    }
}
