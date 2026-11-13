package com.genlogs.app.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.genlogs.app.dto.ServicioResponse;
import com.genlogs.app.model.CategoriaServicio;
import com.genlogs.app.model.Servicio;
import com.genlogs.app.model.UnidadMedida;

/** S1: costo, margen y precio manual del servicio solo para Gerente General, Jefe Administrativo y Asistente Logístico. */
class ServicioCostoVisibleTest {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static void comoRol(String rol) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "u", "x", List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    static Servicio servicio() {
        CategoriaServicio padre = new CategoriaServicio();
        padre.setIdCategoriaServicio(1);
        padre.setNombreCategoria("Mantenimiento");
        padre.setSlugWeb("mantenimiento");
        CategoriaServicio categoria = new CategoriaServicio();
        categoria.setIdCategoriaServicio(2);
        categoria.setNombreCategoria("Bombas");
        categoria.setSlugWeb("bombas");
        categoria.setCategoriaPadre(padre);
        UnidadMedida unidad = new UnidadMedida();
        unidad.setIdUnidadMedida(3);
        unidad.setCodigoUnidad("SRV");
        unidad.setNombreUnidad("Servicio");

        Servicio s = new Servicio();
        s.setIdServicio(7L);
        s.setCodigoServicio("S-7");
        s.setNombreServicio("Mantenimiento de bomba");
        s.setCategoriaServicio(categoria);
        s.setUnidadMedida(unidad);
        s.setCosto(new BigDecimal("1000"));
        s.setMargen(new BigDecimal("25"));
        s.setPrecioReferencial(new BigDecimal("1250"));
        s.setPrecioManual(false);
        return s;
    }

    @Test
    void losTresRolesConPermisoVenCostoMargenYPrecioManual() {
        for (String rol : List.of("GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO")) {
            comoRol(rol);
            ServicioResponse visto = ServicioResponse.de(servicio(), ServicioController.veCostoYMargen());
            assertThat(visto.costo()).as(rol).isEqualByComparingTo("1000");
            assertThat(visto.margen()).as(rol).isEqualByComparingTo("25");
            assertThat(visto.precioManual()).as(rol).isFalse();
        }
    }

    @Test
    void losDemasVenElPrecioPeroNoElCostoNiElMargen() {
        for (String rol : List.of("CONTADOR_GENERAL", "SUPERVISOR_SST", "ASISTENTE_ADMINISTRATIVO", "CLIENTE")) {
            comoRol(rol);
            Servicio original = servicio();
            ServicioResponse visto = ServicioResponse.de(original, ServicioController.veCostoYMargen());
            assertThat(visto.costo()).as(rol).isNull();
            assertThat(visto.margen()).as(rol).isNull();
            assertThat(visto.precioManual()).as(rol).isNull();
            assertThat(visto.precioReferencial()).as(rol).isEqualByComparingTo("1250");
            assertThat(visto.nombreServicio()).isEqualTo("Mantenimiento de bomba");
            // La entidad no se toca (no se guarda nada en la base).
            assertThat(original.getCosto()).isEqualByComparingTo("1000");
        }
    }

    @Test
    void mantieneLaFormaQueUsaElFrontend() {
        ServicioResponse visto = ServicioResponse.de(servicio(), true);
        assertThat(visto.categoriaServicio().idCategoriaServicio()).isEqualTo(2);
        assertThat(visto.categoriaServicio().categoriaPadre().idCategoriaServicio()).isEqualTo(1);
        assertThat(visto.unidadMedida().idUnidadMedida()).isEqualTo(3);
        assertThat(visto.status()).isEqualTo("A");
    }

    @Test
    void sinSesionNoVeCostoYUnServicioVacioNoRompe() {
        assertThat(ServicioController.veCostoYMargen()).isFalse();
        assertThat(ServicioResponse.de(null, false)).isNull();
        ServicioResponse vacio = ServicioResponse.de(new Servicio(), true);
        assertThat(vacio.categoriaServicio()).isNull();
        assertThat(vacio.unidadMedida()).isNull();
    }
}