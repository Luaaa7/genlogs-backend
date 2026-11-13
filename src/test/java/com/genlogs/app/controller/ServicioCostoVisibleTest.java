package com.genlogs.app.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.genlogs.app.model.Servicio;

/** Costo y margen del servicio solo para Gerente General, Jefe Administrativo y Asistente Logístico. */
class ServicioCostoVisibleTest {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static void comoRol(String rol) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "u", "x", List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    private static Servicio servicio() {
        Servicio s = new Servicio();
        s.setIdServicio(7L);
        s.setNombreServicio("Mantenimiento de bomba");
        s.setCosto(new BigDecimal("1000"));
        s.setMargen(new BigDecimal("25"));
        s.setPrecioReferencial(new BigDecimal("1250"));
        return s;
    }

    @Test
    void losTresRolesConPermisoVenCostoYMargen() {
        for (String rol : List.of("GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO")) {
            comoRol(rol);
            Servicio visto = ServicioController.paraElRol(servicio(), ServicioController.veCostoYMargen());
            assertThat(visto.getCosto()).as(rol).isEqualByComparingTo("1000");
            assertThat(visto.getMargen()).as(rol).isEqualByComparingTo("25");
        }
    }

    @Test
    void losDemasVenElPrecioPeroNoElCostoNiElMargen() {
        for (String rol : List.of("CONTADOR_GENERAL", "SUPERVISOR_SST", "ASISTENTE_ADMINISTRATIVO", "CLIENTE")) {
            comoRol(rol);
            Servicio original = servicio();
            Servicio visto = ServicioController.paraElRol(original, ServicioController.veCostoYMargen());
            assertThat(visto.getCosto()).as(rol).isNull();
            assertThat(visto.getMargen()).as(rol).isNull();
            assertThat(visto.getPrecioReferencial()).as(rol).isEqualByComparingTo("1250");
            assertThat(visto.getNombreServicio()).isEqualTo("Mantenimiento de bomba");
            // La entidad original no se toca (no se guarda nada en la base).
            assertThat(original.getCosto()).isEqualByComparingTo("1000");
        }
    }

    @Test
    void sinSesionNoVeCosto() {
        assertThat(ServicioController.veCostoYMargen()).isFalse();
        assertThat(ServicioController.paraElRol(null, false)).isNull();
    }
}