package com.genlogs.app.service;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.SectorEconomico;
import com.genlogs.app.repository.SectorEconomicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SectorEconomicoService {

    private final SectorEconomicoRepository sectorEconomicoRepository;

    @Transactional(readOnly = true)
    public List<SectorEconomico> listarTodos() {
        return sectorEconomicoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<SectorEconomico> listarActivos() {
        return sectorEconomicoRepository.findByStatus("A");
    }

    @Transactional(readOnly = true)
    public SectorEconomico buscarPorId(Integer id) {
        return sectorEconomicoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sector económico no encontrado"));
    }

    @Transactional
    public SectorEconomico registrar(SectorEconomico nuevo) {
        if (sectorEconomicoRepository.existsByNombreSectorIgnoreCase(nuevo.getNombreSector())) {
            throw new BusinessException("Ya existe un sector económico con el nombre: " + nuevo.getNombreSector());
        }
        nuevo.setStatus("A");
        nuevo.setUserCreate(usuarioActual());
        nuevo.setProcessCreate("ALTA_SECTOR_ECONOMICO");
        nuevo.setDateCreate(LocalDateTime.now());
        return sectorEconomicoRepository.save(nuevo);
    }

    @Transactional
    public SectorEconomico actualizar(Integer id, SectorEconomico datos) {
        SectorEconomico existente = buscarPorId(id);
        existente.setNombreSector(datos.getNombreSector());
        existente.setDescripcion(datos.getDescripcion());
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("EDICION_SECTOR_ECONOMICO");
        existente.setDateUpdate(LocalDateTime.now());
        return sectorEconomicoRepository.save(existente);
    }

    @Transactional
    public void desactivar(Integer id) {
        SectorEconomico existente = buscarPorId(id);
        existente.setStatus("I");
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("BAJA_SECTOR_ECONOMICO");
        existente.setDateUpdate(LocalDateTime.now());
        sectorEconomicoRepository.save(existente);
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
