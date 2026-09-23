package com.genlogs.app.service;

import com.genlogs.app.dto.FacturacionResponse;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Facturacion;
import com.genlogs.app.repository.FacturacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FacturacionService {

    private final FacturacionRepository facturacionRepository;

    @Transactional(readOnly = true)
    public FacturacionResponse buscarPorId(Long idFacturacion) {
        Facturacion f = facturacionRepository.findById(idFacturacion)
                .orElseThrow(() -> new ResourceNotFoundException("Facturación no encontrada"));
        return toResponse(f);
    }

    @Transactional(readOnly = true)
    public FacturacionResponse buscarPorComprobante(String serie, String numero) {
        Facturacion f = facturacionRepository.findBySerieComprobanteAndNumeroComprobante(serie, numero)
                .orElseThrow(() -> new ResourceNotFoundException("Facturación no encontrada"));
        return toResponse(f);
    }

    @Transactional(readOnly = true)
    public List<FacturacionResponse> listarPorCliente(Long idCliente) {
        return facturacionRepository.findByCliente(idCliente)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private FacturacionResponse toResponse(Facturacion f) {
        return FacturacionResponse.builder()
                .idFacturacion(f.getIdFacturacion())
                .numeroComprobante(f.getSerieComprobante() + "-" + f.getNumeroComprobante())
                .clienteRazonSocial(f.getOrdenCompra().getCotizacion().getCliente().getTercero().getRazonSocial())
                .fechaEmision(f.getFechaEmision())
                .fechaVencimiento(f.getFechaVencimiento())
                .montoPagado(f.getMontoPagado())
                .estado(f.getEstadoFacturacion().getNombreEstado())
                .build();
    }
}
