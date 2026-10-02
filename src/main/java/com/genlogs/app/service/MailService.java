package com.genlogs.app.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Envío de correos por la API HTTPS de Brevo (https://www.brevo.com).
 * Se usa HTTP en vez de SMTP porque Render bloquea los puertos SMTP en el plan gratuito.
 *
 * Si BREVO_API_KEY no está configurada, el enlace se escribe en los logs
 * (útil para probar sin correo real).
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    @Value("${app.mail.brevo-api-key:}")
    private String apiKey;

    @Value("${app.mail.from-email:}")
    private String fromEmail;

    @Value("${app.mail.from-name:GenLogs}")
    private String fromName;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public void enviarRecuperacion(String destino, String nombres, String link) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("BREVO_API_KEY no configurada. Enlace de recuperación (válido 30 min): {}", link);
            return;
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            log.error("MAIL_FROM_EMAIL no configurada. Debe ser un remitente verificado en Brevo.");
            return;
        }

        String html = "<div style=\"font-family:Arial,sans-serif;max-width:480px\">"
                + "<h2>Recuperar contraseña</h2>"
                + "<p>Hola " + escaparHtml(nombres) + ",</p>"
                + "<p>Recibimos una solicitud para cambiar tu contraseña de GenLogs. "
                + "El enlace es válido por 30 minutos.</p>"
                + "<p><a href=\"" + escaparHtml(link) + "\" "
                + "style=\"background:#1E3A5F;color:#fff;padding:10px 18px;border-radius:6px;text-decoration:none\">"
                + "Cambiar contraseña</a></p>"
                + "<p style=\"color:#666;font-size:12px\">Si no fuiste tú, ignora este mensaje.</p>"
                + "</div>";

        String json = "{"
                + "\"sender\":{\"name\":" + jsonString(fromName) + ",\"email\":" + jsonString(fromEmail) + "},"
                + "\"to\":[{\"email\":" + jsonString(destino) + ",\"name\":" + jsonString(nombres == null ? "" : nombres) + "}],"
                + "\"subject\":" + jsonString("Recupera tu contraseña - GenLogs") + ","
                + "\"htmlContent\":" + jsonString(html)
                + "}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .timeout(Duration.ofSeconds(15))
                    .header("api-key", apiKey)
                    .header("accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                log.error("Brevo respondió {}: {}", response.statusCode(), response.body());
            } else {
                log.info("Correo de recuperación enviado");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Envío de correo interrumpido", e);
        } catch (Exception e) {
            log.error("No se pudo enviar el correo de recuperación", e);
        }
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static String escaparHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}