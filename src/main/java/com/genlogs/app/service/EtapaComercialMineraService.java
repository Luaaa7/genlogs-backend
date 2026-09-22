package com.genlogs.app.service;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.EtapaComercialMinera;
import com.genlogs.app.repository.EtapaComercialMineraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EtapaComercialMineraService {

    private final EtapaComercialMineraRepository etapaComercialMineraRepository;

    @Transactional(readOnly = true)
    public List<EtapaComercialMinera> listarTodos() {
        return etapaComercialMineraRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<EtapaComercialMinera> listarActivos() {
        return etapaComercialMineraRepository.findByStatus("A");
    }

    @Transactional(readOnly = true)
    public EtapaComercialMinera buscarPorId(Integer id) {
        return etapaComercialMineraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Etapa comercial minera no encontrada"));
    }

    @Transactional
    public EtapaComercialMinera registrar(EtapaComercialMinera nueva) {
        if (etapaComercialMineraRepository.existsByNombreEtapaIgnoreCase(nueva.getNombreEtapa())) {
            throw new BusinessException("Ya existe una etapa comercial con el nombre: " + nueva.getNombreEtapa());
        }
        if (nueva.getOrdenFlujo() == null || nueva.getOrdenFlujo() <= 0) {
            throw new BusinessException("El orden de flujo debe ser mayor a 0");
        }
        nueva.setStatus("A");
        nueva.setUserCreate(usuarioActual());
        nueva.setProcessCreate("ALTA_ETAPA_COMERCIAL_MINERA");
        nueva.setDateCreate(LocalDateTime.now());
        return etapaComercialMineraRepository.save(nueva);
    }

    @Transactional
    public EtapaComercialMinera actualizar(Integer id, EtapaComercialMinera datos) {
        EtapaComercialMinera existente = buscarPorId(id);
        existente.setNombreEtapa(datos.getNombreEtapa());
        existente.setOrdenFlujo(datos.getOrdenFlujo());
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("EDICION_ETAPA_COMERCIAL_MINERA");
        existente.setDateUpdate(LocalDateTime.now());
        return etapaComercialMineraRepository.save(existente);
    }

    @Transactional
    public void desactivar(Integer id) {
        EtapaComercialMinera existente = buscarPorId(id);
        existente.setStatus("I");
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("BAJA_ETAPA_COMERCIAL_MINERA");
        existente.setDateUpdate(LocalDateTime.now());
        etapaComercialMineraRepository.save(existente);
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
