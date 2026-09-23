package com.genlogs.app.service;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.EmpresaMinera;
import com.genlogs.app.model.EmpresaMineraMineral;
import com.genlogs.app.model.EmpresaMineraMineralId;
import com.genlogs.app.model.Mineral;
import com.genlogs.app.repository.EmpresaMineraMineralRepository;
import com.genlogs.app.repository.EmpresaMineraRepository;
import com.genlogs.app.repository.MineralRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tabla de asociación (minerales que extrae cada unidad minera). Sin id
 * propio, por eso las bajas son borrado físico (deleteById), no status "I".
 */
@Service
@RequiredArgsConstructor
public class EmpresaMineraMineralService {

    private final EmpresaMineraMineralRepository empresaMineraMineralRepository;
    private final EmpresaMineraRepository empresaMineraRepository;
    private final MineralRepository mineralRepository;

    @Transactional(readOnly = true)
    public List<EmpresaMineraMineral> listarPorEmpresaMinera(Long idEmpresaMinera) {
        return empresaMineraMineralRepository.findByEmpresaMinera_IdEmpresaMinera(idEmpresaMinera);
    }

    @Transactional
    public EmpresaMineraMineral asociar(Long idEmpresaMinera, Integer idMineral, Boolean esPrincipal) {
        EmpresaMineraMineralId id = new EmpresaMineraMineralId(idEmpresaMinera, idMineral);
        if (empresaMineraMineralRepository.existsById(id)) {
            throw new BusinessException("Esa unidad minera ya tiene asociado ese mineral");
        }

        EmpresaMinera empresaMinera = empresaMineraRepository.findById(idEmpresaMinera)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa minera no encontrada"));
        Mineral mineral = mineralRepository.findById(idMineral)
                .orElseThrow(() -> new ResourceNotFoundException("Mineral no encontrado"));

        EmpresaMineraMineral nueva = EmpresaMineraMineral.builder()
                .empresaMinera(empresaMinera)
                .mineral(mineral)
                .esPrincipal(Boolean.TRUE.equals(esPrincipal))
                .build();
        nueva.setStatus("A");
        nueva.setUserCreate(usuarioActual());
        nueva.setProcessCreate("ALTA_EMPRESA_MINERA_MINERAL");
        nueva.setDateCreate(LocalDateTime.now());
        return empresaMineraMineralRepository.save(nueva);
    }

    @Transactional
    public void desasociar(Long idEmpresaMinera, Integer idMineral) {
        EmpresaMineraMineralId id = new EmpresaMineraMineralId(idEmpresaMinera, idMineral);
        if (!empresaMineraMineralRepository.existsById(id)) {
            throw new ResourceNotFoundException("Esa asociación empresa-mineral no existe");
        }
        empresaMineraMineralRepository.deleteById(id);
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
