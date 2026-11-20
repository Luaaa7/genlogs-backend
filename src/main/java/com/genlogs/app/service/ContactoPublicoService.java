package com.genlogs.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.genlogs.app.dto.ContactoPublicoRequest;

/**
 * «Contáctame» (BR2). El destino y la introducción salen de configuración
 * porque el texto todavía está por confirmar con Karla y Mell.
 */
@Service
public class ContactoPublicoService {

    private static final Logger log = LoggerFactory.getLogger(ContactoPublicoService.class);

    private final LimitadorContactoPublico limitador;
    private final CorreoSaliente correo;

    @Value("${app.mail.contacto-destino:}")
    private String destino;

    @Value("${app.mail.contacto-asunto:Consulta desde la web de GENLOGS}")
    private String asunto;

    @Value("${app.mail.contacto-introduccion:Mensaje recibido desde el formulario de contacto.}")
    private String introduccion;

    public ContactoPublicoService(LimitadorContactoPublico limitador, CorreoSaliente correo) {
        this.limitador = limitador;
        this.correo = correo;
    }

    /** @return true si el correo salió. El honeypot responde true y no envía nada. */
    public boolean recibir(ContactoPublicoRequest request, String ip) {
        if (request.getSitioWeb() != null && !request.getSitioWeb().isBlank()) {
            log.info("Contacto público descartado por el campo anti-bot");
            return true;
        }
        limitador.verificar(ip);
        String texto = texto(request);
        String html = html(request);
        String destinoEnvio = destino;
        if (destino == null || destino.isBlank()) {
            log.warn("CONTACTO_EMAIL no configurado. No se envió el mensaje de contacto.");
            destinoEnvio = null;
        }
        return correo.enviar(destinoEnvio, "GENLOGS", asunto, html, texto, "contacto", texto);
    }

    private String texto(ContactoPublicoRequest request) {
        String telefono = request.getTelefono() == null || request.getTelefono().isBlank()
                ? "No indicó teléfono"
                : request.getTelefono().trim();
        return introduccion + "\n\n"
                + "Nombre: " + request.getNombre().trim() + "\n"
                + "Correo: " + request.getCorreo().trim() + "\n"
                + "Teléfono: " + telefono + "\n\n"
                + request.getMensaje().trim();
    }

    private String html(ContactoPublicoRequest request) {
        String telefono = request.getTelefono() == null ? "" : request.getTelefono();
        return "<p>" + MailService.escaparHtml(introduccion) + "</p>"
                + "<p><strong>Nombre:</strong> " + MailService.escaparHtml(request.getNombre()) + "<br>"
                + "<strong>Correo:</strong> " + MailService.escaparHtml(request.getCorreo()) + "<br>"
                + "<strong>Teléfono:</strong> " + MailService.escaparHtml(telefono) + "</p>"
                + "<p>" + MailService.escaparHtml(request.getMensaje()).replace("\n", "<br>") + "</p>";
    }
}
