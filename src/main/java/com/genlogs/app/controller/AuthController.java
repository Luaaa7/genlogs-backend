package com.genlogs.app.controller;

import java.util.Map;

import com.genlogs.app.dto.ForgotPasswordRequest;
import com.genlogs.app.dto.LoginRequest;
import com.genlogs.app.dto.LoginResponse;
import com.genlogs.app.dto.ResetPasswordRequest;
import com.genlogs.app.service.AuthService;
import com.genlogs.app.service.PasswordResetService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String MENSAJE_SOLICITUD =
            "Si el correo está registrado, recibirás instrucciones para restablecer tu contraseña.";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> solicitarReset(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        passwordResetService.solicitarReset(request.getCorreo());
        return ResponseEntity.ok(Map.of("message", MENSAJE_SOLICITUD));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> restablecer(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        passwordResetService.restablecer(
                request.getToken(),
                request.getNuevaPassword()
        );

        return ResponseEntity.ok(
                Map.of("message", "La contraseña fue actualizada. Ya puedes iniciar sesión.")
        );
    }
}