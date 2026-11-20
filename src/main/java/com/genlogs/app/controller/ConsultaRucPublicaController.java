package com.genlogs.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.RazonSocialPublicaResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.service.FactilizaService;
import com.genlogs.app.service.IpCliente;
import com.genlogs.app.service.LimitadorConsultaRuc;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Razón social de un RUC para el registro público (RU3, RU5).
 * /api/public/** ya es permitAll: esta ruta no pide token.
 *
 * <p>Contrato «sin dato»: 200 y {@code razonSocial} null. El frontend deja
 * escribir la razón social a mano. No se usa 404 ni 500 cuando Factiliza
 * no está, falla o no responde. Un RUC que no tiene 11 dígitos o no empieza
 * por 10 o 20 responde 400 «Ingresa un RUC válido»; un DNI no se consulta.</p>
 */
@RestController
@RequestMapping("/api/public/ruc")
@RequiredArgsConstructor
public class ConsultaRucPublicaController {

    private final FactilizaService factilizaService;
    private final LimitadorConsultaRuc limitador;

    @GetMapping("/{ruc}")
    public ResponseEntity<RazonSocialPublicaResponse> consultar(@PathVariable String ruc, HttpServletRequest request) {
        if (ruc == null || !ruc.matches("^(10|20)\\d{9}$")) {
            throw new BusinessException("Ingresa un RUC válido");
        }
        limitador.verificar(IpCliente.de(request));
        return ResponseEntity.ok(new RazonSocialPublicaResponse(factilizaService.consultarRazonSocialPublica(ruc)));
    }
}
