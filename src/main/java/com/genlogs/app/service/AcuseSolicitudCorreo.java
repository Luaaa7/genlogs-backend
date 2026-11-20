package com.genlogs.app.service;

import org.springframework.stereotype.Service;

/**
 * Acuse de una solicitud creada desde la landing o la vitrina (BR3).
 * El texto lleva el código y no lleva precios, costos ni márgenes.
 */
@Service
public class AcuseSolicitudCorreo {

    static final String ASUNTO = "Recibimos tu solicitud";

    private final CorreoSaliente correo;

    public AcuseSolicitudCorreo(CorreoSaliente correo) {
        this.correo = correo;
    }

    public void enviar(String destino, String nombre, String codigo) {
        String texto = texto(codigo);
        String html = "<p>Hola " + MailService.escaparHtml(nombre) + ",</p>"
                + "<p>" + MailService.escaparHtml(texto) + "</p>"
                + "<p style=\"color:#6b7280;font-size:12px\">Aviso automático de GenLogs.</p>";
        correo.enviar(destino, nombre, ASUNTO, html, texto, "acuse de solicitud");
    }

    /** Texto fijo. No se arma con importes de la solicitud. */
    static String texto(String codigo) {
        String visible = codigo == null ? "" : codigo.trim();
        return "Recibimos tu solicitud " + visible
                + ". La estamos revisando y te avisaremos por este correo cuando haya novedades.";
    }
}
