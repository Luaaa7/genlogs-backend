package com.genlogs.app.service;

import com.genlogs.app.dto.CotizacionResponse;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.repository.CotizacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CotizacionService {

    private final CotizacionRepository cotizacionRepository;

    @Transactional(readOnly = true)
    public CotizacionResponse buscarPorId(Long idCotizacion) {
        Cotizacion c = cotizacionRepository.findById(idCotizacion)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public CotizacionResponse buscarPorCodigo(String codigoCotizacion) {
        Cotizacion c = cotizacionRepository.findByCodigoCotizacion(codigoCotizacion)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public List<CotizacionResponse> listarPorCliente(Long idCliente) {
        return cotizacionRepository.findByCliente(idCliente)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private CotizacionResponse toResponse(Cotizacion c) {
        return CotizacionResponse.builder()
                .idCotizacion(c.getIdCotizacion())
                .codigoCotizacion(c.getCodigoCotizacion())
                .clienteRazonSocial(c.getCliente().getTercero().getRazonSocial())
                .fechaCotizacion(c.getFechaCotizacion())
                .fechaValidez(c.getFechaValidez())
                .estado(c.getEstadoCotizacion().getNombreEstado())
                .total(c.getTotal())
                .moneda(c.getMoneda().getSimbolo())
                .build();
    }
}
