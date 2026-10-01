package com.genlogs.app.controller;

import com.genlogs.app.dto.ForgotPasswordRequest;
import com.genlogs.app.dto.LoginRequest;
import com.genlogs.app.dto.LoginResponse;
import com.genlogs.app.dto.ResetPasswordRequest;
import com.genlogs.app.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Solicita el enlace de recuperación. Siempre responde 200 (exista o no el correo)
     * para no revelar qué correos están registrados.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.solicitarRecuperacion(request.getCorreo());
        return ResponseEntity.ok().build();
    }

    /** Cambia la contraseña usando el token recibido por correo. */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.restablecerPassword(request.getToken(), request.getNuevaPassword());
        return ResponseEntity.ok().build();
    }
}