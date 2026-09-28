package com.genlogs.app.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.RolResponse;
import com.genlogs.app.repository.RolRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RolService {

    private final RolRepository rolRepository;

    @Transactional(readOnly = true)
    public List<RolResponse> listarTodos() {
        return rolRepository.findAll().stream()
                .map(r -> RolResponse.builder()
                        .idRol(r.getIdRol())
                        .nombreRol(r.getNombreRol())
                        .descripcion(r.getDescripcion())
                        .build())
                .toList();
    }
}