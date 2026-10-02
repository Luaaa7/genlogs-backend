package com.genlogs.app.controller;

import com.genlogs.app.dto.ForgotPasswordRequest;
import com.genlogs.app.dto.LoginRequest;
import com.genlogs.app.dto.LoginResponse;
import com.genlogs.app.dto.ResetPasswordRequest;
import com.genlogs.app.dto.TokenResetInfoResponse;
import com.genlogs.app.dto.ValidarTokenResetRequest;
import com.genlogs.app.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.solicitarRecuperacion(request.getCorreo());
        // Respuesta siempre igual, exista o no el correo (no revelar qué correos están registrados).
        return ResponseEntity.ok(Map.of("message",
                "Si el correo está registrado, recibirás un enlace para cambiar tu contraseña."));
    }

    /** La pantalla de "nueva contraseña" lo consulta al abrirse: indica si el enlace sigue vigente. */
    @PostMapping("/validate-reset-token")
    public ResponseEntity<TokenResetInfoResponse> validarTokenReset(@Valid @RequestBody ValidarTokenResetRequest request) {
        return ResponseEntity.ok(authService.validarTokenReset(request.getToken()));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.restablecerPassword(request.getToken(), request.getNuevaPassword());
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente."));
    }
}