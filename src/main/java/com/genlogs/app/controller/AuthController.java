package com.genlogs.app.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.ForgotPasswordRequest;
import com.genlogs.app.dto.LoginRequest;
import com.genlogs.app.dto.LoginResponse;
import com.genlogs.app.dto.ResetPasswordRequest;
import com.genlogs.app.dto.TokenResetInfoResponse;
import com.genlogs.app.dto.ValidarTokenResetRequest;
import com.genlogs.app.service.AuthService;
import com.genlogs.app.service.PasswordResetService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.solicitarReset(request.getCorreo());
        // Respuesta siempre igual, exista o no el correo (no revelar qué correos están registrados).
        return ResponseEntity.ok(Map.of("message",
                "Si el correo está registrado, recibirás un enlace para cambiar tu contraseña."));
    }

    /** La pantalla de "nueva contraseña" lo consulta al abrirse: indica si el enlace sigue vigente. */
    @PostMapping("/validate-reset-token")
    public ResponseEntity<TokenResetInfoResponse> validarTokenReset(@Valid @RequestBody ValidarTokenResetRequest request) {
        return ResponseEntity.ok(passwordResetService.validarToken(request.getToken()));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.restablecer(request.getToken(), request.getNuevaPassword());
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente."));
    }
}

