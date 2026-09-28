package com.genlogs.app.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.EstadoFacturacionResponse;
import com.genlogs.app.repository.EstadoFacturacionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EstadoFacturacionService {

    private final EstadoFacturacionRepository estadoFacturacionRepository;

    @Transactional(readOnly = true)
    public List<EstadoFacturacionResponse> listarTodos() {
        return estadoFacturacionRepository.findAll().stream()
                .map(e -> EstadoFacturacionResponse.builder()
                        .idEstadoFacturacion(e.getIdEstadoFacturacion())
                        .codigoEstado(e.getCodigoEstado())
                        .nombreEstado(e.getNombreEstado())
                        .esFinal(e.getEsFinal())
                        .build())
                .toList();
    }
}
