package com.genlogs.app.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.pagos.PagoEnLineaService;

import lombok.RequiredArgsConstructor;

/**
 * Aviso (webhook) de Mercado Pago. Es público (/api/public/**) porque llega sin sesión:
 * se acepta solo con la firma x-signature válida, y el pago se vuelve a consultar a Mercado Pago.
 */
@RestController
@RequestMapping("/api/public/pagos")
@RequiredArgsConstructor
public class PagoAvisoController {

    private final PagoEnLineaService pagoEnLineaService;

    @PostMapping("/mercadopago")
    public ResponseEntity<Void> mercadoPago(
            @RequestHeader(name = "x-signature", required = false) String xSignature,
            @RequestHeader(name = "x-request-id", required = false) String xRequestId,
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(name = "type", required = false) String tipo,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        String id = dataId != null ? dataId : idDelCuerpo(cuerpo);
        String clase = tipo != null ? tipo : (cuerpo != null && cuerpo.get("type") != null ? cuerpo.get("type").toString() : null);
        try {
            boolean aceptado = pagoEnLineaService.recibirAviso(xSignature, xRequestId, id, clase);
            return aceptado ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (BusinessException e) {
            // Mercado Pago no respondió: 503 para que reintente más tarde.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    private static String idDelCuerpo(Map<String, Object> cuerpo) {
        if (cuerpo == null || !(cuerpo.get("data") instanceof Map<?, ?> data) || data.get("id") == null) {
            return null;
        }
        return data.get("id").toString();
    }
}