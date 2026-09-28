package com.genlogs.app.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.EstadoOrdenCompraResponse;
import com.genlogs.app.repository.EstadoOrdenCompraRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EstadoOrdenCompraService {

    private final EstadoOrdenCompraRepository estadoOrdenCompraRepository;

    @Transactional(readOnly = true)
    public List<EstadoOrdenCompraResponse> listarTodos() {
        return estadoOrdenCompraRepository.findAll().stream()
                .map(e -> EstadoOrdenCompraResponse.builder()
                        .idEstadoOrdenCompra(e.getIdEstadoOrdenCompra())
                        .codigoEstado(e.getCodigoEstado())
                        .nombreEstado(e.getNombreEstado())
                        .esFinal(e.getEsFinal())
                        .build())
                .toList();
    }
}
