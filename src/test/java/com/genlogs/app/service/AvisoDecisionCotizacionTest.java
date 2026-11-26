package com.genlogs.app.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.UsuarioRepository;

/** Cuando el Jefe aprueba o devuelve una cotización, quien la preparó recibe el aviso con el enlace. */
@ExtendWith(MockitoExtension.class)
class AvisoDecisionCotizacionTest {

    @Mock private CotizacionRepository cotizaciones;
    @Mock private UsuarioRepository usuarios;
    @Mock private NotificacionService notificaciones;

    private AvisoDecisionCotizacion aviso;

    @BeforeEach
    void preparar() {
        aviso = new AvisoDecisionCotizacion(cotizaciones, usuarios, notificaciones);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("jefe", "x", List.of()));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void alAprobarAvisaAQuienLaPreparoConElEnlace() {
        conCreador("ana", 7L);

        aviso.aprobada(50L);

        verify(notificaciones).avisarUsuario(eq(7L), eq(TipoAviso.COTIZACION_APROBADA), contains("COT-2026-50"),
                any(), eq("/cotizaciones/50"));
    }

    @Test
    void alDevolverAvisaConElMotivo() {
        conCreador("ana", 7L);

        aviso.devuelta(50L, "Falta el costo del ítem 2");

        verify(notificaciones).avisarUsuario(eq(7L), eq(TipoAviso.COTIZACION_DEVUELTA), contains("COT-2026-50"),
                contains("Falta el costo del ítem 2"), eq("/cotizaciones/50"));
    }

    @Test
    void siQuienLaPreparoEsQuienDecideNoSeAvisaASiMismo() {
        conCreador("jefe", 3L);

        aviso.aprobada(50L);

        verify(notificaciones, never()).avisarUsuario(any(), any(), any(), any(), any());
    }

    @Test
    void sinCreadorConocidoNoHaceNadaNiFalla() {
        Cotizacion c = cotizacion("nadie");
        when(cotizaciones.findById(50L)).thenReturn(Optional.of(c));
        when(usuarios.findByNombreUsuario("nadie")).thenReturn(Optional.empty());

        aviso.aprobada(50L);

        verify(notificaciones, never()).avisarUsuario(any(), any(), any(), any(), any());
    }

    private void conCreador(String nombre, Long id) {
        Usuario u = new Usuario();
        u.setIdUsuario(id);
        u.setNombreUsuario(nombre);
        when(cotizaciones.findById(50L)).thenReturn(Optional.of(cotizacion(nombre)));
        org.mockito.Mockito.lenient().when(usuarios.findByNombreUsuario(nombre)).thenReturn(Optional.of(u));
    }

    private static Cotizacion cotizacion(String creador) {
        Cotizacion c = new Cotizacion();
        c.setIdCotizacion(50L);
        c.setCodigoCotizacion("COT-2026-50");
        c.setUserCreate(creador);
        return c;
    }
}
