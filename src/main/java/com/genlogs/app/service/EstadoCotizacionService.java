package com.genlogs.app.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.EstadoCotizacionResponse;
import com.genlogs.app.repository.EstadoCotizacionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EstadoCotizacionService {

    private final EstadoCotizacionRepository estadoCotizacionRepository;

    @Transactional(readOnly = true)
    public List<EstadoCotizacionResponse> listarTodos() {
        return estadoCotizacionRepository.findAll().stream()
                .map(e -> EstadoCotizacionResponse.builder()
                        .idEstadoCotizacion(e.getIdEstadoCotizacion())
                        .codigoEstado(e.getCodigoEstado())
                        .nombreEstado(e.getNombreEstado())
                        .ordenFlujo(e.getOrdenFlujo() != null ? e.getOrdenFlujo().intValue() : null)
                        .esFinal(e.getEsFinal())
                        .build())
                .toList();
    }
}
