package com.genlogs.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;

/**
 * Envío de mejor esfuerzo (BR5, BR6). Si Brevo falla, el proceso de negocio
 * sigue: se anota el fallo (sin la API key) y se avisa en la campana del
 * Jefe Administrativo. No se reintenta. {@code app.mail.reintentos} es el
 * único punto para activar reintentos; en 0 se envía una sola vez.
 */
@Service
public class CorreoSaliente {

    private static final Logger log = LoggerFactory.getLogger(CorreoSaliente.class);

    private final MailService mailService;
    private final NotificacionService notificacionService;

    /** Reintentos extra. 0 = un solo envío. Por confirmar con el grupo. */
    @Value("${app.mail.reintentos:0}")
    private int reintentos;

    public CorreoSaliente(MailService mailService, NotificacionService notificacionService) {
        this.mailService = mailService;
        this.notificacionService = notificacionService;
    }

    public boolean enviar(String destino, String nombre, String asunto, String html, String texto, String contexto) {
        return enviar(destino, nombre, asunto, html, texto, contexto, null);
    }

    /**
     * @param detalleCampana texto del aviso si el envío falla. Si viene vacío, el aviso
     *        solo dice que falló el asunto. Contáctame pasa aquí el mensaje completo del visitante.
     */
    public boolean enviar(String destino, String nombre, String asunto, String html, String texto, String contexto,
                          String detalleCampana) {
        int veces = 1 + Math.max(0, reintentos);
        boolean enviado = false;
        for (int i = 0; i < veces && !enviado; i++) {
            try {
                enviado = mailService.enviar(destino, nombre, asunto, html, texto);
            } catch (RuntimeException e) {
                log.warn("Falló el envío de correo ({})", contexto);
            }
        }
        if (!enviado) {
            log.warn("No se envió el correo ({})", contexto);
            avisarEnCampana(asunto, detalleCampana);
        }
        return enviado;
    }

    private void avisarEnCampana(String asunto, String detalleCampana) {
        try {
            String titulo = asunto == null || asunto.isBlank() ? "un correo" : asunto;
            String mensaje = detalleCampana == null || detalleCampana.isBlank()
                    ? "Falló el envío de \"" + titulo + "\". Revisa la configuración de Brevo."
                    : detalleCampana;
            notificacionService.avisarRol(RolSistema.JEFE_ADMINISTRATIVO, TipoAviso.GENERAL,
                    "No se pudo enviar un correo", mensaje, "/usuarios", false);
        } catch (RuntimeException e) {
            log.warn("Tampoco se pudo dejar el aviso en la campana");
        }
    }
}
