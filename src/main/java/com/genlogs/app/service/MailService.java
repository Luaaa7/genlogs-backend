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

import com.genlogs.app.security.JwtUtil;

/**
 * Envío de correos por la API HTTPS de Resend (https://resend.com).
 * Se usa HTTP en vez de SMTP porque Render bloquea los puertos SMTP en el plan gratuito.
 *
 * Si RESEND_API_KEY no está configurada, el enlace se escribe en los logs
 * (útil para probar sin correo real).
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    @Value("${app.mail.resend-api-key:}")
    private String apiKey;

    @Value("${app.mail.from}")
    private String from;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public void enviarRecuperacion(String destino, String nombres, String nombreUsuario, String link) {
        int minutos = JwtUtil.RESET_EXPIRATION_MINUTES;

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("RESEND_API_KEY no configurada. Enlace de recuperación (válido {} min): {}", minutos, link);
            return;
        }

        String json = "{"
                + "\"from\":" + jsonString(from) + ","
                + "\"to\":[" + jsonString(destino) + "],"
                + "\"subject\":" + jsonString("Restablece tu contraseña de GenLogs") + ","
                + "\"html\":" + jsonString(construirHtml(nombres, nombreUsuario, link, minutos)) + ","
                + "\"text\":" + jsonString(construirTexto(nombres, nombreUsuario, link, minutos))
                + "}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                log.error("Resend respondió {}: {}", response.statusCode(), response.body());
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

    // ------------------------------------------------------------------
    // Plantillas
    // ------------------------------------------------------------------

    private String construirTexto(String nombres, String usuario, String link, int minutos) {
        return "Hola " + nombres + ",\n\n"
                + "Recibimos una solicitud para restablecer la contraseña de tu cuenta de GenLogs.\n\n"
                + "Tu usuario: " + usuario + "\n\n"
                + "Para elegir una nueva contraseña abre este enlace (vence en " + minutos + " minutos y solo se puede usar una vez):\n"
                + link + "\n\n"
                + "Si no solicitaste este cambio, ignora este mensaje: tu contraseña seguirá siendo la misma.\n\n"
                + "GenLogs S.A.C. - General Logistic Solutions";
    }

    private String construirHtml(String nombres, String usuario, String link, int minutos) {
        String n = escaparHtml(nombres);
        String u = escaparHtml(usuario);
        String l = escaparHtml(link);

        return """
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta name="color-scheme" content="light only">
<title>Restablece tu contraseña</title>
</head>
<body style="margin:0;padding:0;background-color:#EEF2F7;">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;color:#EEF2F7;">
Usa este enlace para elegir una nueva contraseña. Vence en __MIN__ minutos.
</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#EEF2F7;">
<tr><td align="center" style="padding:32px 12px;">

  <table role="presentation" width="560" cellpadding="0" cellspacing="0" border="0" style="width:100%;max-width:560px;">

    <!-- Encabezado -->
    <tr><td align="center" bgcolor="#1E3A5F" style="background-color:#1E3A5F;border-radius:16px 16px 0 0;padding:30px 24px;">
      <div style="font-family:Arial,Helvetica,sans-serif;font-size:26px;font-weight:bold;letter-spacing:1px;color:#FFFFFF;">GENLOGS S.A.C.</div>
      <div style="font-family:Arial,Helvetica,sans-serif;font-size:11px;letter-spacing:2px;color:#9DB7D5;margin-top:6px;">GENERAL LOGISTIC SOLUTIONS</div>
    </td></tr>

    <!-- Cuerpo -->
    <tr><td bgcolor="#FFFFFF" style="background-color:#FFFFFF;padding:36px 32px 12px 32px;font-family:Arial,Helvetica,sans-serif;color:#1F2937;">

      <h1 style="margin:0 0 16px 0;font-size:22px;line-height:28px;color:#1E3A5F;">Restablece tu contraseña</h1>

      <p style="margin:0 0 14px 0;font-size:15px;line-height:24px;">Hola <strong>__NOMBRES__</strong>,</p>
      <p style="margin:0 0 22px 0;font-size:15px;line-height:24px;">
        Recibimos una solicitud para cambiar la contraseña de tu cuenta de GenLogs.
        Pulsa el botón para elegir una nueva.
      </p>

      <!-- Usuario -->
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 26px 0;">
      <tr><td bgcolor="#F1F5F9" style="background-color:#F1F5F9;border-left:4px solid #2B6CB0;border-radius:6px;padding:12px 16px;">
        <div style="font-size:12px;color:#64748B;text-transform:uppercase;letter-spacing:1px;">Tu usuario</div>
        <div style="font-size:17px;font-weight:bold;color:#1E3A5F;margin-top:2px;">__USUARIO__</div>
      </td></tr>
      </table>

      <!-- Botón -->
      <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center" style="margin:0 auto 24px auto;">
      <tr><td align="center" bgcolor="#1E3A5F" style="background-color:#1E3A5F;border-radius:8px;">
        <a href="__LINK__" target="_blank"
           style="display:inline-block;padding:14px 36px;font-family:Arial,Helvetica,sans-serif;font-size:16px;font-weight:bold;color:#FFFFFF;text-decoration:none;border-radius:8px;">
          Cambiar contraseña
        </a>
      </td></tr>
      </table>

      <!-- Aviso de vigencia -->
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 22px 0;">
      <tr><td bgcolor="#FFF7ED" style="background-color:#FFF7ED;border:1px solid #FED7AA;border-radius:8px;padding:12px 16px;font-size:13px;line-height:20px;color:#9A3412;">
        <strong>Este enlace vence en __MIN__ minutos</strong> y solo se puede usar una vez.
        Si vence, solicita uno nuevo desde la pantalla de inicio de sesión.
      </td></tr>
      </table>

      <p style="margin:0 0 6px 0;font-size:12px;line-height:18px;color:#64748B;">
        ¿El botón no funciona? Copia y pega este enlace en tu navegador:
      </p>
      <p style="margin:0 0 24px 0;font-size:12px;line-height:18px;word-break:break-all;">
        <a href="__LINK__" target="_blank" style="color:#2B6CB0;">__LINK__</a>
      </p>

      <hr style="border:none;border-top:1px solid #E5E7EB;margin:0 0 18px 0;">

      <p style="margin:0 0 20px 0;font-size:13px;line-height:20px;color:#6B7280;">
        <strong>¿No fuiste tú?</strong> Ignora este mensaje. Tu contraseña seguirá siendo la misma y nadie podrá cambiarla sin este enlace.
      </p>
    </td></tr>

    <!-- Pie -->
    <tr><td align="center" bgcolor="#F8FAFC" style="background-color:#F8FAFC;border-radius:0 0 16px 16px;padding:18px 24px;font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:18px;color:#94A3B8;">
      Este es un mensaje automático, por favor no respondas a este correo.<br>
      &copy; GenLogs S.A.C. &middot; General Logistic Solutions
    </td></tr>

  </table>

</td></tr>
</table>
</body>
</html>
"""
                .replace("__NOMBRES__", n)
                .replace("__USUARIO__", u)
                .replace("__LINK__", l)
                .replace("__MIN__", String.valueOf(minutos));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

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