package com.genlogs.app.service;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.repository.MonedaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MonedaService {

    private final MonedaRepository monedaRepository;

    @Transactional(readOnly = true)
    public List<Moneda> listarTodos() {
        return monedaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Moneda> listarActivos() {
        return monedaRepository.findByStatus("A");
    }

    @Transactional(readOnly = true)
    public Moneda buscarPorId(Integer id) {
        return monedaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Moneda no encontrada"));
    }

    @Transactional
    public Moneda registrar(Moneda nueva) {
        if (monedaRepository.existsByCodigoMonedaIgnoreCase(nueva.getCodigoMoneda())) {
            throw new BusinessException("Ya existe una moneda con el código: " + nueva.getCodigoMoneda());
        }
        nueva.setStatus("A");
        nueva.setUserCreate(usuarioActual());
        nueva.setProcessCreate("ALTA_MONEDA");
        nueva.setDateCreate(LocalDateTime.now());
        return monedaRepository.save(nueva);
    }

    @Transactional
    public Moneda actualizar(Integer id, Moneda datos) {
        Moneda existente = buscarPorId(id);
        existente.setCodigoMoneda(datos.getCodigoMoneda());
        existente.setNombreMoneda(datos.getNombreMoneda());
        existente.setSimbolo(datos.getSimbolo());
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("EDICION_MONEDA");
        existente.setDateUpdate(LocalDateTime.now());
        return monedaRepository.save(existente);
    }

    @Transactional
    public void desactivar(Integer id) {
        Moneda existente = buscarPorId(id);
        existente.setStatus("I");
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("BAJA_MONEDA");
        existente.setDateUpdate(LocalDateTime.now());
        monedaRepository.save(existente);
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
