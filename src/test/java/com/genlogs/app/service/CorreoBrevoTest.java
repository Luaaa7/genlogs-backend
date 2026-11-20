package com.genlogs.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.genlogs.app.dto.ContactoPublicoRequest;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;

/** BR2 a BR6 con Brevo simulado. */
class CorreoBrevoTest {

    private MailService mail;
    private NotificacionService avisos;
    private CorreoSaliente correo;

    @BeforeEach
    void setUp() {
        mail = mock(MailService.class);
        avisos = mock(NotificacionService.class);
        correo = new CorreoSaliente(mail, avisos);
        ReflectionTestUtils.setField(correo, "reintentos", 0);
    }

    @Test
    void elAcuseSeEnviaUnaVezConElCodigoYSinPrecios() {
        when(mail.enviar(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        AcuseSolicitudCorreo acuse = new AcuseSolicitudCorreo(correo);

        acuse.enviar("ana@cliente.pe", "Ana <script>", "SOL-2026-15");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(mail, times(1)).enviar(eq("ana@cliente.pe"), eq("Ana <script>"), eq(AcuseSolicitudCorreo.ASUNTO),
                html.capture(), texto.capture());
        assertThat(texto.getValue()).contains("SOL-2026-15");
        assertThat(texto.getValue().toLowerCase()).doesNotContain("precio", "costo", "margen");
        assertThat(html.getValue()).contains("SOL-2026-15").doesNotContain("<script>");
        assertThat(html.getValue()).contains("&lt;script&gt;");
        verify(avisos, never()).avisarRol(any(), any(), anyString(), anyString(), any(), anyBoolean());
    }

    @Test
    void siBrevoCaeElAcuseNoLanzaYAvisaEnLaCampana() {
        when(mail.enviar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Brevo caido"));
        AcuseSolicitudCorreo acuse = new AcuseSolicitudCorreo(correo);

        acuse.enviar("ana@cliente.pe", "Ana", "SOL-2026-15");

        verify(mail, times(1)).enviar(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(avisos).avisarRol(eq(RolSistema.JEFE_ADMINISTRATIVO), eq(TipoAviso.GENERAL),
                eq("No se pudo enviar un correo"), contains("Brevo"), eq("/usuarios"), eq(false));
    }

    @Test
    void elContactoEscapaElMensajeYNoReintenta() {
        when(mail.enviar(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        ContactoPublicoService contacto = new ContactoPublicoService(new LimitadorContactoPublico(), correo);
        ReflectionTestUtils.setField(contacto, "destino", "karla@genlogs.pe");
        ReflectionTestUtils.setField(contacto, "asunto", "Consulta web");
        ReflectionTestUtils.setField(contacto, "introduccion", "Intro por confirmar");
        ContactoPublicoRequest request = new ContactoPublicoRequest();
        request.setNombre("Luis");
        request.setCorreo("luis@mina.pe");
        request.setMensaje("Hola <b>precio</b>");

        assertThat(contacto.recibir(request, "203.0.113.8")).isTrue();

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mail, times(1)).enviar(eq("karla@genlogs.pe"), anyString(), eq("Consulta web"), html.capture(), contains("luis@mina.pe"));
        assertThat(html.getValue()).contains("&lt;b&gt;").doesNotContain("<b>");
        verify(avisos, never()).avisarRol(any(), any(), anyString(), anyString(), any(), anyBoolean());
    }

    @Test
    void siBrevoCaeElContactoRespondeFalsoYGuardaElMensajeCompleto() {
        when(mail.enviar(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        ContactoPublicoService contacto = new ContactoPublicoService(new LimitadorContactoPublico(), correo);
        ReflectionTestUtils.setField(contacto, "destino", "karla@genlogs.pe");
        ReflectionTestUtils.setField(contacto, "asunto", "Consulta web");
        ReflectionTestUtils.setField(contacto, "introduccion", "Intro por confirmar");
        ContactoPublicoRequest request = new ContactoPublicoRequest();
        request.setNombre("Luis");
        request.setCorreo("luis@mina.pe");
        request.setTelefono("999111222");
        request.setMensaje("Necesito fajas para la unidad. Precio aparte.");

        assertThat(contacto.recibir(request, "203.0.113.7")).isFalse();

        verify(mail, times(1)).enviar(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(avisos).avisarRol(eq(RolSistema.JEFE_ADMINISTRATIVO), eq(TipoAviso.GENERAL),
                eq("No se pudo enviar un correo"),
                contains("Necesito fajas para la unidad. Precio aparte."),
                eq("/usuarios"), eq(false));
    }

    @Test
    void elHoneypotNoEnviaCorreo() {
        ContactoPublicoService contacto = new ContactoPublicoService(new LimitadorContactoPublico(), correo);
        ContactoPublicoRequest request = new ContactoPublicoRequest();
        request.setNombre("Bot");
        request.setCorreo("bot@spam.pe");
        request.setMensaje("compra");
        request.setSitioWeb("http://spam");

        assertThat(contacto.recibir(request, "203.0.113.8")).isTrue();

        verify(mail, never()).enviar(anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
