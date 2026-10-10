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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetEmailService {

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public void enviarEnlaceReset(String correo, String token) {
        String resetUrl = UriComponentsBuilder.fromUriString(frontendUrl)
                .pathSegment("reset-password")
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();

        String url = "https://api.brevo.com/v3/smtp/email";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", brevoApiKey);

        String contenidoTexto = "Recibimos una solicitud para restablecer tu contraseña.\n\n"
                + "Abre este enlace dentro de los próximos 30 minutos:\n"
                + resetUrl
                + "\n\nSi no solicitaste el cambio, ignora este correo.";

        Map<String, Object> body = Map.of(
            "sender", Map.of("email", from, "name", "GENLOGS"),
            "to", List.of(Map.of("email", correo)),
            "subject", "Restablecer contraseña - GENLOGS",
            "textContent", contenidoTexto
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("Correo de recuperación enviado exitosamente a través de la API HTTP de Brevo. Status: {}", response.getStatusCode());
        } catch (Exception e) {
            log.error("Error al enviar el correo mediante la API HTTP de Brevo", e);
            throw new RuntimeException("No se pudo enviar el correo de recuperación", e);
        }
    }
}