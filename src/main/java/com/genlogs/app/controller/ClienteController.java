package com.genlogs.app.controller;

import com.genlogs.app.dto.ClienteRequest;
import com.genlogs.app.dto.ClienteResponse;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.service.ClienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','VENDEDOR')")
    public ResponseEntity<ClienteResponse> registrar(@Valid @RequestBody ClienteRequest request) {
        ClienteResponse response = clienteService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{idCliente}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','VENDEDOR')")
    public ResponseEntity<ClienteResponse> actualizar(@PathVariable Long idCliente,
                                                      @Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.ok(clienteService.actualizar(idCliente, request));
    }

    @GetMapping("/{idCliente}")
    public ResponseEntity<ClienteResponse> buscarPorId(@PathVariable Long idCliente) {
        return ResponseEntity.ok(clienteService.buscarPorId(idCliente));
    }

    @GetMapping("/documento/{numeroDocumento}")
    public ResponseEntity<ClienteResponse> buscarPorDocumento(@PathVariable String numeroDocumento) {
        return ResponseEntity.ok(clienteService.buscarPorDocumento(numeroDocumento));
    }

    @GetMapping
    public ResponseEntity<List<Cliente>> listarActivos(
            @RequestParam(required = false) String razonSocial,
            @RequestParam(required = false) String documento,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        List<Cliente> clientes = clienteService.buscarActivos(razonSocial, documento, page, size);
        // Con paginación, el total va en una cabecera para no cambiar el formato
        // de la respuesta (otras pantallas siguen recibiendo una lista).
        if (size != null && size > 0) {
            return ResponseEntity.ok()
                    .header("X-Total-Count", String.valueOf(clienteService.contarActivos(razonSocial, documento)))
                    .body(clientes);
        }
        return ResponseEntity.ok(clientes);
    }

    @DeleteMapping("/{idCliente}")
    public ResponseEntity<Void> desactivar(@PathVariable Long idCliente) {
        clienteService.desactivar(idCliente);
        return ResponseEntity.noContent().build();
    }
}