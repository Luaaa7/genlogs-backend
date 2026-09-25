package com.genlogs.app.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordResetEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public void enviarEnlaceReset(String correo, String token) {
        String resetUrl = UriComponentsBuilder.fromUriString(frontendUrl)
                .pathSegment("reset-password")
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(from);
        mensaje.setTo(correo);
        mensaje.setSubject("Restablecer contraseña - GENLOGS");
        mensaje.setText(
                "Recibimos una solicitud para restablecer tu contraseña.\n\n"
                + "Abre este enlace dentro de los próximos 30 minutos:\n"
                + resetUrl
                + "\n\nSi no solicitaste el cambio, ignora este correo."
        );

        mailSender.send(mensaje);
    }
}
