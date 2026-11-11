package com.genlogs.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Notificacion;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.NotificacionRepository;
import com.genlogs.app.repository.UsuarioRepository;

/** Paso 0 - NotificacionService (D-21, D-25). */
class NotificacionServiceTest {

    private NotificacionRepository repo;
    private UsuarioRepository usuarios;
    private MailService mail;
    private NotificacionService servicio;

    @BeforeEach
    void setUp() {
        repo = mock(NotificacionRepository.class);
        usuarios = mock(UsuarioRepository.class);
        mail = mock(MailService.class);
        when(repo.save(any(Notificacion.class))).thenAnswer(inv -> inv.getArgument(0));
        servicio = new NotificacionService(repo, usuarios, mail);
    }

    private static Usuario usuario(long id, String nombreUsuario, String correo) {
        Usuario u = new Usuario();
        u.setIdUsuario(id);
        u.setNombreUsuario(nombreUsuario);
        u.setNombres("Persona " + id);
        u.setCorreo(correo);
        return u;
    }

    @Test
    void avisarRol_creaUnAvisoPorUsuarioYSinCorreoSiNoSePide() {
        when(repo.usuariosActivosDelRol("ASISTENTE_LOGISTICO"))
                .thenReturn(List.of(usuario(1, "ana", "ana@x.pe"), usuario(2, "luis", "luis@x.pe")));

        int enviados = servicio.avisarRol(RolSistema.ASISTENTE_LOGISTICO, TipoAviso.PLAZO_POR_VENCER,
                "SOL-2026-8 al 75 %", "Vence hoy a las 18:00", "/solicitudes/8", false);

        assertThat(enviados).isEqualTo(2);
        verify(repo, times(2)).save(any(Notificacion.class));
        verify(mail, never()).enviarCorreo(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void avisarRol_conCorreo_enviaYLoMarca() {
        when(repo.usuariosActivosDelRol("GERENTE_GENERAL")).thenReturn(List.of(usuario(5, "gerente", "g@x.pe")));
        when(mail.enviarCorreo(eq("g@x.pe"), anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        servicio.avisarRol(RolSistema.GERENTE_GENERAL, TipoAviso.ALTO_VALOR,
                "Cotizacion de alto valor", "COT-2026-15 por S/ 25 000", "/cotizaciones/15", true);

        ArgumentCaptor<Notificacion> guardado = ArgumentCaptor.forClass(Notificacion.class);
        verify(repo).save(guardado.capture());
        assertThat(guardado.getValue().getTipo()).isEqualTo("ALTO_VALOR");
        assertThat(guardado.getValue().getIdUsuario()).isEqualTo(5L);
        assertThat(guardado.getValue().getCorreoEnviado()).isTrue();
        assertThat(guardado.getValue().getLeida()).isFalse();
    }

    @Test
    void siFallaElCorreo_elAvisoSeGuardaIgual() {
        when(repo.usuariosActivosDelRol("GERENTE_GENERAL")).thenReturn(List.of(usuario(5, "gerente", "g@x.pe")));
        when(mail.enviarCorreo(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Brevo caido"));

        int enviados = servicio.avisarRol(RolSistema.GERENTE_GENERAL, TipoAviso.ALTO_VALOR, "Alto valor", "x", null, true);

        assertThat(enviados).isEqualTo(1);
        ArgumentCaptor<Notificacion> guardado = ArgumentCaptor.forClass(Notificacion.class);
        verify(repo).save(guardado.capture());
        assertThat(guardado.getValue().getCorreoEnviado()).isFalse();
    }

    @Test
    void marcarLeida_avisoDeOtraPersona_da404() {
        when(usuarios.findByNombreUsuario("ana")).thenReturn(Optional.of(usuario(1, "ana", "a@x.pe")));
        when(repo.findByIdNotificacionAndIdUsuario(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.marcarLeida(99L, "ana")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void marcarLeida_avisoPropio_quedaLeido() {
        Notificacion aviso = new Notificacion();
        aviso.setIdNotificacion(7L);
        aviso.setIdUsuario(1L);
        when(usuarios.findByNombreUsuario("ana")).thenReturn(Optional.of(usuario(1, "ana", "a@x.pe")));
        when(repo.findByIdNotificacionAndIdUsuario(7L, 1L)).thenReturn(Optional.of(aviso));

        servicio.marcarLeida(7L, "ana");

        assertThat(aviso.getLeida()).isTrue();
        assertThat(aviso.getFechaLectura()).isNotNull();
    }

    @Test
    void avisoSinTitulo_esRechazado() {
        assertThatThrownBy(() -> servicio.avisarUsuario(1L, TipoAviso.GENERAL, " ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}