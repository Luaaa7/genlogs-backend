package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.ConfirmarPagoRequest;
import com.genlogs.app.dto.PagoIniciadoResponse;
import com.genlogs.app.dto.PortalComprobanteResponse;
import com.genlogs.app.dto.ResultadoPagoResponse;
import com.genlogs.app.pagos.PagoEnLineaService;
import com.genlogs.app.service.PermisosCotizacion;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Portal: el cliente ve los comprobantes de su empresa y paga en línea (BPMN 4.1). */
@RestController
@RequestMapping("/api/portal/comprobantes")
@PreAuthorize(PermisosCotizacion.PORTAL)
@RequiredArgsConstructor
public class PortalComprobanteController {

    private final PagoEnLineaService pagoEnLineaService;

    @GetMapping
    public ResponseEntity<List<PortalComprobanteResponse>> listar() {
        return ResponseEntity.ok(pagoEnLineaService.misComprobantes());
    }

    /** Crea el cobro en Mercado Pago por el saldo pendiente y devuelve el enlace de pago. */
    @PostMapping("/{id}/pagar")
    public ResponseEntity<PagoIniciadoResponse> pagar(@PathVariable Long id) {
        return ResponseEntity.ok(pagoEnLineaService.iniciar(id));
    }

    /** Regreso del checkout: consulta el pago a Mercado Pago y registra el cobro si corresponde. */
    @PostMapping("/pago/confirmar")
    public ResponseEntity<ResultadoPagoResponse> confirmar(@Valid @RequestBody ConfirmarPagoRequest request) {
        return ResponseEntity.ok(pagoEnLineaService.confirmarDesdePortal(request.idPago()));
    }
}