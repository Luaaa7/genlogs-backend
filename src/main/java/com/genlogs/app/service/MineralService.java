package com.genlogs.app.service;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Mineral;
import com.genlogs.app.repository.MineralRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MineralService {

    private final MineralRepository mineralRepository;

    @Transactional(readOnly = true)
    public List<Mineral> listarTodos() {
        return mineralRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Mineral> listarActivos() {
        return mineralRepository.findByStatus("A");
    }

    @Transactional(readOnly = true)
    public Mineral buscarPorId(Integer id) {
        return mineralRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mineral no encontrado"));
    }

    @Transactional
    public Mineral registrar(Mineral nuevo) {
        if (mineralRepository.existsByNombreMineralIgnoreCase(nuevo.getNombreMineral())) {
            throw new BusinessException("Ya existe un mineral con el nombre: " + nuevo.getNombreMineral());
        }
        nuevo.setStatus("A");
        nuevo.setUserCreate(usuarioActual());
        nuevo.setProcessCreate("ALTA_MINERAL");
        nuevo.setDateCreate(LocalDateTime.now());
        return mineralRepository.save(nuevo);
    }

    @Transactional
    public Mineral actualizar(Integer id, Mineral datos) {
        Mineral existente = buscarPorId(id);
        existente.setNombreMineral(datos.getNombreMineral());
        existente.setSimbolo(datos.getSimbolo());
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("EDICION_MINERAL");
        existente.setDateUpdate(LocalDateTime.now());
        return mineralRepository.save(existente);
    }

    @Transactional
    public void desactivar(Integer id) {
        Mineral existente = buscarPorId(id);
        existente.setStatus("I");
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("BAJA_MINERAL");
        existente.setDateUpdate(LocalDateTime.now());
        mineralRepository.save(existente);
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
