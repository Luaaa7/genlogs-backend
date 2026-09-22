package com.genlogs.app.service;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.CategoriaServicio;
import com.genlogs.app.repository.CategoriaServicioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaServicioService {

    private final CategoriaServicioRepository categoriaServicioRepository;

    @Transactional(readOnly = true)
    public List<CategoriaServicio> listarTodos() {
        return categoriaServicioRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<CategoriaServicio> listarActivos() {
        return categoriaServicioRepository.findByStatus("A");
    }

    @Transactional(readOnly = true)
    public CategoriaServicio buscarPorId(Integer id) {
        return categoriaServicioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría de servicio no encontrada"));
    }

    @Transactional
    public CategoriaServicio registrar(CategoriaServicio nueva) {
        if (categoriaServicioRepository.existsBySlugWebIgnoreCase(nueva.getSlugWeb())) {
            throw new BusinessException("Ya existe una categoría con el slug: " + nueva.getSlugWeb());
        }
        // Si mandan categoriaPadre solo con el id, la resolvemos a la entidad real.
        if (nueva.getCategoriaPadre() != null && nueva.getCategoriaPadre().getIdCategoriaServicio() != null) {
            CategoriaServicio padre = buscarPorId(nueva.getCategoriaPadre().getIdCategoriaServicio());
            nueva.setCategoriaPadre(padre);
        }
        nueva.setStatus("A");
        nueva.setUserCreate(usuarioActual());
        nueva.setProcessCreate("ALTA_CATEGORIA_SERVICIO");
        nueva.setDateCreate(LocalDateTime.now());
        return categoriaServicioRepository.save(nueva);
    }

    @Transactional
    public CategoriaServicio actualizar(Integer id, CategoriaServicio datos) {
        CategoriaServicio existente = buscarPorId(id);
        existente.setNombreCategoria(datos.getNombreCategoria());
        existente.setSlugWeb(datos.getSlugWeb());
        if (datos.getCategoriaPadre() != null && datos.getCategoriaPadre().getIdCategoriaServicio() != null) {
            existente.setCategoriaPadre(buscarPorId(datos.getCategoriaPadre().getIdCategoriaServicio()));
        } else {
            existente.setCategoriaPadre(null);
        }
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("EDICION_CATEGORIA_SERVICIO");
        existente.setDateUpdate(LocalDateTime.now());
        return categoriaServicioRepository.save(existente);
    }

    @Transactional
    public void desactivar(Integer id) {
        CategoriaServicio existente = buscarPorId(id);
        existente.setStatus("I");
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("BAJA_CATEGORIA_SERVICIO");
        existente.setDateUpdate(LocalDateTime.now());
        categoriaServicioRepository.save(existente);
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
