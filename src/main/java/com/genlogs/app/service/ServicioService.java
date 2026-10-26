package com.genlogs.app.service;

import com.genlogs.app.dto.ServicioRequest;
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
    public Servicio registrar(ServicioRequest request) {
        if (servicioRepository.existsByCodigoServicioIgnoreCase(request.getCodigoServicio())) {
            throw new BusinessException("Ya existe un servicio con el código: " + request.getCodigoServicio());
        }
        Servicio nuevo = new Servicio();
        nuevo.setCodigoServicio(request.getCodigoServicio());
        nuevo.setNombreServicio(request.getNombreServicio());
        nuevo.setDescripcion(request.getDescripcion());
        nuevo.setCategoriaServicio(resolverCategoriaPorId(request.getIdCategoriaServicio()));
        nuevo.setUnidadMedida(resolverUnidadMedidaPorId(request.getIdUnidadMedida()));
        nuevo.setDuracionEstimadaHoras(request.getDuracionEstimadaHoras());
        if (request.getVisibleWeb() == null) {
            nuevo.setVisibleWeb(true);
        } else {
            nuevo.setVisibleWeb(request.getVisibleWeb());
        }
        nuevo.setStatus("A");
        nuevo.setUserCreate(usuarioActual());
        nuevo.setProcessCreate("ALTA_SERVICIO");
        nuevo.setDateCreate(LocalDateTime.now());
        return servicioRepository.save(nuevo);
    }

    @Transactional
    public Servicio actualizar(Long id, ServicioRequest datos) {
        Servicio existente = buscarPorId(id);
        existente.setCategoriaServicio(resolverCategoriaPorId(datos.getIdCategoriaServicio()));
        existente.setUnidadMedida(resolverUnidadMedidaPorId(datos.getIdUnidadMedida()));
        existente.setCodigoServicio(datos.getCodigoServicio());
        existente.setNombreServicio(datos.getNombreServicio());
        existente.setDuracionEstimadaHoras(datos.getDuracionEstimadaHoras());
        if (datos.getVisibleWeb() != null) {
            existente.setVisibleWeb(datos.getVisibleWeb());
        }
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

    private CategoriaServicio resolverCategoriaPorId(Integer id) {
        if (id == null) {
            throw new BusinessException("Debe indicar la categoría del servicio");
        }
        return categoriaServicioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría de servicio no existe"));
    }

    private UnidadMedida resolverUnidadMedidaPorId(Integer id) {
        if (id == null) {
            throw new BusinessException("Debe indicar la unidad de medida del servicio");
        }
        return unidadMedidaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida no existe"));
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
