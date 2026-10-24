
package com.genlogs.app.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PasswordResetEmailService {

    /**
     * Credenciales y configuración del correo.
     * Los valores por defecto evitan errores de resolución de placeholders
     * cuando las variables de entorno no están configuradas.
     */
    @Value("${app.mail.brevo-api-key:}")
    private String brevoApiKey;

    @Value("${app.mail.from-email:}")
    private String from;

    @Value("${app.mail.from-name:GenLogs}")
    private String fromName;

    @Value("${app.frontend-url:}")
    private String frontendUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public void enviarEnlaceReset(String correo, String token) {

        // Validar los datos recibidos.
        if (correo == null || correo.isBlank()) {
            throw new IllegalArgumentException(
                    "El correo del destinatario es obligatorio");
        }

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "El token de recuperación es obligatorio");
        }

        // Verificar la configuración de Brevo.
        if (brevoApiKey == null || brevoApiKey.isBlank()
                || from == null || from.isBlank()) {

            log.warn(
                    "Brevo no está configurado. No se pudo enviar el correo de recuperación a {}.",
                    correo);

            throw new IllegalStateException(
                    "El servicio de correo no está configurado correctamente");
        }

        // Verificar la URL del frontend.
        if (frontendUrl == null || frontendUrl.isBlank()) {
            log.error(
                    "La propiedad app.frontend-url no está configurada");

            throw new IllegalStateException(
                    "La URL del frontend no está configurada");
        }

        // Construir el enlace de recuperación.
        String resetUrl = UriComponentsBuilder
                .fromUriString(frontendUrl.trim())
                .pathSegment("reset-password")
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();

        String url = "https://api.brevo.com/v3/smtp/email";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", brevoApiKey.trim());

        String contenidoTexto =
                "Recibimos una solicitud para restablecer su contraseña.\n\n"
                + "Abre este enlace para continuar con el proceso de recuperación:\n"
                + resetUrl
                + "\n\nSi no solicitaste el cambio, ignora este correo.";

        Map<String, Object> body = Map.of(
                "sender", Map.of(
                        "email", from.trim(),
                        "name", fromName == null || fromName.isBlank()
                                ? "GenLogs"
                                : fromName.trim()),
                "to", List.of(
                        Map.of("email", correo.trim())),
                "subject", "Restablecer contraseña - GENLOGS",
                "textContent", contenidoTexto
        );

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    request,
                    String.class);

            log.info(
                    "Solicitud de envío del correo de recuperación procesada por Brevo. Estado HTTP: {}",
                    response.getStatusCode());

        } catch (Exception e) {
            log.error(
                    "Error al enviar el correo de recuperación mediante Brevo",
                    e);

            throw new RuntimeException(
                    "No se pudo enviar el correo de recuperación",
                    e);
        }
    }
}
