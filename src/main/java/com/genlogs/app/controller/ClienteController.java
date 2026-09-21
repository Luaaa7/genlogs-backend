package com.genlogs.app.controller;

import com.genlogs.app.dto.ClienteRequest;
import com.genlogs.app.dto.ClienteResponse;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.service.ClienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @PostMapping
    // @PreAuthorize("hasAnyRole('ADMINISTRADOR','VENDEDOR')") // lo agrega Luana con MethodSecurityConfig
    public ResponseEntity<ClienteResponse> registrar(@Valid @RequestBody ClienteRequest request) {
        ClienteResponse response = clienteService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
    public ResponseEntity<List<Cliente>> listarActivos() {
        return ResponseEntity.ok(clienteService.listarActivos());
    }

    @DeleteMapping("/{idCliente}")
    public ResponseEntity<Void> desactivar(@PathVariable Long idCliente) {
        clienteService.desactivar(idCliente);
        return ResponseEntity.noContent().build();
    }
}
