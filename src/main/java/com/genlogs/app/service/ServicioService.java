package com.genlogs.app.service;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.CategoriaServicio;
import com.genlogs.app.model.Servicio;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.repository.CategoriaServicioRepository;
import com.genlogs.app.repository.ServicioRepository;
import com.genlogs.app.repository.UnidadMedidaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicioService {

    private final ServicioRepository servicioRepository;
    private final CategoriaServicioRepository categoriaServicioRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;

    @Transactional(readOnly = true)
    public List<Servicio> listarTodos() {
        return servicioRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Servicio> listarActivos() {
        return servicioRepository.findByStatus("A");
    }

    @Transactional(readOnly = true)
    public List<Servicio> listarPorCategoria(Integer idCategoriaServicio) {
        return servicioRepository.findByCategoriaServicio_IdCategoriaServicio(idCategoriaServicio);
    }

    @Transactional(readOnly = true)
    public Servicio buscarPorId(Long id) {
        return servicioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado"));
    }

    @Transactional
    public Servicio registrar(Servicio nuevo) {
        if (servicioRepository.existsByCodigoServicioIgnoreCase(nuevo.getCodigoServicio())) {
            throw new BusinessException("Ya existe un servicio con el código: " + nuevo.getCodigoServicio());
        }
        nuevo.setCategoriaServicio(resolverCategoria(nuevo.getCategoriaServicio()));
        nuevo.setUnidadMedida(resolverUnidadMedida(nuevo.getUnidadMedida()));
        if (nuevo.getVisibleWeb() == null) {
            nuevo.setVisibleWeb(true);
        }
        nuevo.setStatus("A");
        nuevo.setUserCreate(usuarioActual());
        nuevo.setProcessCreate("ALTA_SERVICIO");
        nuevo.setDateCreate(LocalDateTime.now());
        return servicioRepository.save(nuevo);
    }

    @Transactional
    public Servicio actualizar(Long id, Servicio datos) {
        Servicio existente = buscarPorId(id);
        existente.setCategoriaServicio(resolverCategoria(datos.getCategoriaServicio()));
        existente.setUnidadMedida(resolverUnidadMedida(datos.getUnidadMedida()));
        existente.setCodigoServicio(datos.getCodigoServicio());
        existente.setNombreServicio(datos.getNombreServicio());
        existente.setDuracionEstimadaHoras(datos.getDuracionEstimadaHoras());
        existente.setVisibleWeb(datos.getVisibleWeb());
        existente.setDescripcion(datos.getDescripcion());
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("EDICION_SERVICIO");
        existente.setDateUpdate(LocalDateTime.now());
        return servicioRepository.save(existente);
    }

    @Transactional
    public void desactivar(Long id) {
        Servicio existente = buscarPorId(id);
        existente.setStatus("I");
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("BAJA_SERVICIO");
        existente.setDateUpdate(LocalDateTime.now());
        servicioRepository.save(existente);
    }

    // ------------------------------------------------------------------

    private CategoriaServicio resolverCategoria(CategoriaServicio ref) {
        if (ref == null || ref.getIdCategoriaServicio() == null) {
            throw new BusinessException("Debe indicar la categoría del servicio");
        }
        return categoriaServicioRepository.findById(ref.getIdCategoriaServicio())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría de servicio no existe"));
    }

    private UnidadMedida resolverUnidadMedida(UnidadMedida ref) {
        if (ref == null || ref.getIdUnidadMedida() == null) {
            throw new BusinessException("Debe indicar la unidad de medida del servicio");
        }
        return unidadMedidaRepository.findById(ref.getIdUnidadMedida())
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida no existe"));
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
