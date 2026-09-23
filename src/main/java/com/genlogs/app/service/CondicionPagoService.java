package com.genlogs.app.service;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.CondicionPago;
import com.genlogs.app.repository.CondicionPagoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CondicionPagoService {

    private final CondicionPagoRepository condicionPagoRepository;

    @Transactional(readOnly = true)
    public List<CondicionPago> listarTodos() {
        return condicionPagoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<CondicionPago> listarActivos() {
        return condicionPagoRepository.findByStatus("A");
    }

    @Transactional(readOnly = true)
    public CondicionPago buscarPorId(Integer id) {
        return condicionPagoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Condición de pago no encontrada"));
    }

    @Transactional
    public CondicionPago registrar(CondicionPago nueva) {
        if (condicionPagoRepository.existsByNombreCondicionIgnoreCase(nueva.getNombreCondicion())) {
            throw new BusinessException("Ya existe una condición de pago con el nombre: " + nueva.getNombreCondicion());
        }
        nueva.setStatus("A");
        nueva.setUserCreate(usuarioActual());
        nueva.setProcessCreate("ALTA_CONDICION_PAGO");
        nueva.setDateCreate(LocalDateTime.now());
        return condicionPagoRepository.save(nueva);
    }

    @Transactional
    public CondicionPago actualizar(Integer id, CondicionPago datos) {
        CondicionPago existente = buscarPorId(id);
        existente.setNombreCondicion(datos.getNombreCondicion());
        existente.setDiasCredito(datos.getDiasCredito());
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("EDICION_CONDICION_PAGO");
        existente.setDateUpdate(LocalDateTime.now());
        return condicionPagoRepository.save(existente);
    }

    @Transactional
    public void desactivar(Integer id) {
        CondicionPago existente = buscarPorId(id);
        existente.setStatus("I");
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("BAJA_CONDICION_PAGO");
        existente.setDateUpdate(LocalDateTime.now());
        condicionPagoRepository.save(existente);
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
