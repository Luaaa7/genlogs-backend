package com.genlogs.app.service;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.repository.UnidadMedidaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UnidadMedidaService {

    private final UnidadMedidaRepository unidadMedidaRepository;

    @Transactional(readOnly = true)
    public List<UnidadMedida> listarTodos() {
        return unidadMedidaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<UnidadMedida> listarActivos() {
        return unidadMedidaRepository.findByStatus("A");
    }

    @Transactional(readOnly = true)
    public UnidadMedida buscarPorId(Integer id) {
        return unidadMedidaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida no encontrada"));
    }

    @Transactional
    public UnidadMedida registrar(UnidadMedida nueva) {
        if (unidadMedidaRepository.existsByCodigoUnidadIgnoreCase(nueva.getCodigoUnidad())) {
            throw new BusinessException("Ya existe una unidad de medida con el código: " + nueva.getCodigoUnidad());
        }
        nueva.setStatus("A");
        nueva.setUserCreate(usuarioActual());
        nueva.setProcessCreate("ALTA_UNIDAD_MEDIDA");
        nueva.setDateCreate(LocalDateTime.now());
        return unidadMedidaRepository.save(nueva);
    }

    @Transactional
    public UnidadMedida actualizar(Integer id, UnidadMedida datos) {
        UnidadMedida existente = buscarPorId(id);
        existente.setCodigoUnidad(datos.getCodigoUnidad());
        existente.setNombreUnidad(datos.getNombreUnidad());
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("EDICION_UNIDAD_MEDIDA");
        existente.setDateUpdate(LocalDateTime.now());
        return unidadMedidaRepository.save(existente);
    }

    @Transactional
    public void desactivar(Integer id) {
        UnidadMedida existente = buscarPorId(id);
        existente.setStatus("I");
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("BAJA_UNIDAD_MEDIDA");
        existente.setDateUpdate(LocalDateTime.now());
        unidadMedidaRepository.save(existente);
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
