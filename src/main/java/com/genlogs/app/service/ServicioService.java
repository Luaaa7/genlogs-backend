package com.genlogs.app.service;

import com.genlogs.app.enums.EstadoAprobacionServicio;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicioService {

    private final ServicioRepository servicioRepository;
    private final CategoriaServicioRepository categoriaServicioRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;
    private final ParametrosService parametrosService;

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
        aplicarPrecioYAprobacion(nuevo);
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
        existente.setCosto(datos.getCosto());
        existente.setMargen(datos.getMargen());
        existente.setPrecioManual(datos.getPrecioManual());
        existente.setPrecioReferencial(datos.getPrecioReferencial());
        aplicarPrecioYAprobacion(existente);
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

    /** El Jefe pasa un servicio pendiente a APROBADO. El rol lo exige el controlador. */
    @Transactional
    public Servicio aprobar(Long id) {
        Servicio existente = buscarPorId(id);
        if (existente.getEstadoAprobacion() != EstadoAprobacionServicio.PENDIENTE_APROBACION) {
            throw new BusinessException("Este servicio no está pendiente de aprobación");
        }
        existente.setEstadoAprobacion(EstadoAprobacionServicio.APROBADO);
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("APROBACION_SERVICIO");
        existente.setDateUpdate(LocalDateTime.now());
        return servicioRepository.save(existente);
    }

    private void aplicarPrecioYAprobacion(Servicio servicio) {
        exigirCostoYMargen(servicio);
        if (servicio.getPrecioManual() == null) {
            servicio.setPrecioManual(false);
        }
        if (Boolean.TRUE.equals(servicio.getPrecioManual())) {
            servicio.setEstadoAprobacion(estadoDe(servicio.getMargen()));
            return;
        }
        if (servicio.getCosto() != null && servicio.getMargen() != null) {
            servicio.setPrecioReferencial(precioDe(servicio.getCosto(), servicio.getMargen()));
        } else {
            servicio.setPrecioReferencial(null);
        }
        servicio.setEstadoAprobacion(estadoDe(servicio.getMargen()));
    }

    private static void exigirCostoYMargen(Servicio servicio) {
        if (servicio.getCosto() != null && servicio.getCosto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("El costo del servicio tiene que ser mayor que 0");
        }
        if (servicio.getMargen() != null
                && (servicio.getMargen().compareTo(BigDecimal.ZERO) < 0
                || servicio.getMargen().compareTo(new BigDecimal("100")) > 0)) {
            throw new BusinessException("El margen del servicio tiene que estar entre 0 y 100");
        }
    }

    /** costo × (1 + margen/100), a 2 decimales. */
    static BigDecimal precioDe(BigDecimal costo, BigDecimal margen) {
        BigDecimal factor = BigDecimal.ONE.add(margen.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        return costo.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    /** Sin margen no queda pendiente. 15 y 45 siguen adentro; fuera de ese rango, pendiente. */
    private EstadoAprobacionServicio estadoDe(BigDecimal margen) {
        if (margen == null) {
            return EstadoAprobacionServicio.APROBADO;
        }
        BigDecimal minimo = parametrosService.getDecimal(ClavesParametro.MARGEN_SERV_MIN);
        BigDecimal maximo = parametrosService.getDecimal(ClavesParametro.MARGEN_SERV_MAX);
        if (margen.compareTo(minimo) < 0 || margen.compareTo(maximo) > 0) {
            return EstadoAprobacionServicio.PENDIENTE_APROBACION;
        }
        return EstadoAprobacionServicio.APROBADO;
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
