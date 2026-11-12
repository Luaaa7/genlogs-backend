package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.CancelarPedidoRequest;
import com.genlogs.app.dto.CrearSolicitudRequest;
import com.genlogs.app.dto.CrearSolicitudResponse;
import com.genlogs.app.dto.IngresoClienteRequest;
import com.genlogs.app.dto.IngresoClienteResponse;
import com.genlogs.app.dto.PedidoResponse;
import com.genlogs.app.dto.RegistroClienteRequest;
import com.genlogs.app.dto.RegistroClienteResponse;
import com.genlogs.app.service.AuthService;
import com.genlogs.app.service.PortalClienteService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
public class PortalClienteController {

    private final PortalClienteService portalClienteService;
    private final AuthService authService;

    /** Público. Acceso inmediato (D-10): devuelve el token del cliente. */
    @PostMapping("/registro")
    public ResponseEntity<RegistroClienteResponse> registrar(@Valid @RequestBody RegistroClienteRequest request) {
        return ResponseEntity.ok(portalClienteService.registrar(request));
    }

    /** Público. Ingreso del cliente con correo y contraseña, separado del login interno. */
    @PostMapping("/login")
    public ResponseEntity<IngresoClienteResponse> login(@Valid @RequestBody IngresoClienteRequest request) {
        return ResponseEntity.ok(authService.loginCliente(request));
    }

    @GetMapping("/pedidos")
    public ResponseEntity<List<PedidoResponse>> pedidos() {
        return ResponseEntity.ok(portalClienteService.pedidos());
    }

    @GetMapping("/pedidos/{id}")
    public ResponseEntity<PedidoResponse> pedido(@PathVariable Long id) {
        return ResponseEntity.ok(portalClienteService.pedido(id));
    }

    /** El cliente cancela su pedido antes de que se cotice. 204; 409 si ya no se puede. */
    @PatchMapping("/pedidos/{id}/cancelar")
    public ResponseEntity<Void> cancelar(@PathVariable Long id, @Valid @RequestBody CancelarPedidoRequest request) {
        portalClienteService.cancelarPedido(id, request.motivo());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/solicitudes")
    public ResponseEntity<CrearSolicitudResponse> crearSolicitud(@Valid @RequestBody CrearSolicitudRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(portalClienteService.crearSolicitud(request));
    }
}
