package com.genlogs.app.service;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.TipoOperacionMinera;
import com.genlogs.app.repository.TipoOperacionMineraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TipoOperacionMineraService {

    private final TipoOperacionMineraRepository tipoOperacionMineraRepository;

    @Transactional(readOnly = true)
    public List<TipoOperacionMinera> listarTodos() {
        return tipoOperacionMineraRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<TipoOperacionMinera> listarActivos() {
        return tipoOperacionMineraRepository.findByStatus("A");
    }

    @Transactional(readOnly = true)
    public TipoOperacionMinera buscarPorId(Integer id) {
        return tipoOperacionMineraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de operación minera no encontrado"));
    }

    @Transactional
    public TipoOperacionMinera registrar(TipoOperacionMinera nuevo) {
        if (tipoOperacionMineraRepository.existsByNombreOperacionIgnoreCase(nuevo.getNombreOperacion())) {
            throw new BusinessException("Ya existe un tipo de operación con el nombre: " + nuevo.getNombreOperacion());
        }
        nuevo.setStatus("A");
        nuevo.setUserCreate(usuarioActual());
        nuevo.setProcessCreate("ALTA_TIPO_OPERACION_MINERA");
        nuevo.setDateCreate(LocalDateTime.now());
        return tipoOperacionMineraRepository.save(nuevo);
    }

    @Transactional
    public TipoOperacionMinera actualizar(Integer id, TipoOperacionMinera datos) {
        TipoOperacionMinera existente = buscarPorId(id);
        existente.setNombreOperacion(datos.getNombreOperacion());
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("EDICION_TIPO_OPERACION_MINERA");
        existente.setDateUpdate(LocalDateTime.now());
        return tipoOperacionMineraRepository.save(existente);
    }

    @Transactional
    public void desactivar(Integer id) {
        TipoOperacionMinera existente = buscarPorId(id);
        existente.setStatus("I");
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("BAJA_TIPO_OPERACION_MINERA");
        existente.setDateUpdate(LocalDateTime.now());
        tipoOperacionMineraRepository.save(existente);
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
