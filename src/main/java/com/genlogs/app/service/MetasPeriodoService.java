package com.genlogs.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.AccionCorrectivaResponse;
import com.genlogs.app.dto.MetaPeriodoResponse;
import com.genlogs.app.enums.EstadoAccionCorrectiva;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.AccionCorrectiva;
import com.genlogs.app.model.MetaPeriodo;
import com.genlogs.app.model.MetaPeriodoId;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.AccionCorrectivaRepository;
import com.genlogs.app.repository.MetaPeriodoRepository;
import com.genlogs.app.repository.SolicitudCotizacionRepository;
import com.genlogs.app.repository.SolicitudCotizacionRepository.PlazoFila;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.CalculoPlazo.PlazoDeCotizacion;
import com.genlogs.app.service.CalculoPlazo.ResultadoPlazo;

@Service
public class MetasPeriodoService {

    private static final BigDecimal META_POR_DEFECTO = new BigDecimal("90.00");
    private static final BigDecimal CIEN = new BigDecimal("100.00");

    private final MetaPeriodoRepository metaPeriodoRepository;
    private final AccionCorrectivaRepository accionCorrectivaRepository;
    private final SolicitudCotizacionRepository solicitudCotizacionRepository;
    private final UsuarioRepository usuarioRepository;

    public MetasPeriodoService(MetaPeriodoRepository metaPeriodoRepository,
            AccionCorrectivaRepository accionCorrectivaRepository,
            SolicitudCotizacionRepository solicitudCotizacionRepository,
            UsuarioRepository usuarioRepository) {
        this.metaPeriodoRepository = metaPeriodoRepository;
        this.accionCorrectivaRepository = accionCorrectivaRepository;
        this.solicitudCotizacionRepository = solicitudCotizacionRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public MetaPeriodoResponse consultar(int anio, int mes) {
        exigirPeriodo(anio, mes);
        BigDecimal meta = metaDe(anio, mes);
        ResultadoPlazo resultado = CalculoPlazo.calcular(plazos(anio, mes), meta);
        return new MetaPeriodoResponse(anio, mes, meta, resultado.enPlazo(), resultado.consideradas(),
                resultado.porcentaje(), resultado.cumple());
    }

    @Transactional
    public MetaPeriodoResponse actualizarMeta(int anio, int mes, BigDecimal metaPorcentaje) {
        exigirPeriodo(anio, mes);
        BigDecimal valor = exigirMeta(metaPorcentaje);
        MetaPeriodo fila = metaPeriodoRepository.findById(new MetaPeriodoId((short) anio, (short) mes))
                .orElseGet(MetaPeriodo::new);
        fila.setAnio((short) anio);
        fila.setMes((short) mes);
        fila.setMetaPorcentaje(valor);
        metaPeriodoRepository.save(fila);
        return consultar(anio, mes);
    }

    @Transactional(readOnly = true)
    public List<AccionCorrectivaResponse> listarAcciones(int anio, int mes) {
        exigirPeriodo(anio, mes);
        return accionCorrectivaRepository.findByAnioAndMesOrderByFechaAscIdAccionAsc((short) anio, (short) mes)
                .stream()
                .map(MetasPeriodoService::aRespuesta)
                .toList();
    }

    @Transactional
    public AccionCorrectivaResponse registrarAccion(int anio, int mes, String descripcion) {
        String texto = exigirDescripcion(descripcion);
        exigirPeriodo(anio, mes);
        AccionCorrectiva accion = new AccionCorrectiva();
        accion.setAnio((short) anio);
        accion.setMes((short) mes);
        accion.setDescripcion(texto);
        accion.setEstado(EstadoAccionCorrectiva.PENDIENTE);
        accion.setCreadaPor(usuarioActual().getIdUsuario());
        accion.setFecha(LocalDateTime.now());
        return aRespuesta(accionCorrectivaRepository.save(accion));
    }

    @Transactional
    public AccionCorrectivaResponse cambiarEstado(Long id, String estado) {
        AccionCorrectiva accion = accionCorrectivaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la acción " + id));
        accion.setEstado(exigirEstado(estado));
        return aRespuesta(accionCorrectivaRepository.save(accion));
    }

    private List<PlazoDeCotizacion> plazos(int anio, int mes) {
        LocalDate desde = LocalDate.of(anio, mes, 1);
        Map<Long, PlazoDeCotizacion> porCotizacion = new LinkedHashMap<>();
        for (PlazoFila fila : solicitudCotizacionRepository.plazosDelMes(desde, desde.plusMonths(1))) {
            LocalDate limite = fila.getFechaLimiteInterna() == null ? null : fila.getFechaLimiteInterna().toLocalDate();
            porCotizacion.merge(fila.getIdCotizacion(), new PlazoDeCotizacion(fila.getFechaCotizacion(), limite),
                    MetasPeriodoService::quedarseConLimite);
        }
        return List.copyOf(porCotizacion.values());
    }

    /** Si hay varias solicitudes, basta una fecha límite para que la cotización se considere. */
    private static PlazoDeCotizacion quedarseConLimite(PlazoDeCotizacion actual, PlazoDeCotizacion otra) {
        if (actual.fechaLimite() != null) {
            return actual;
        }
        return otra.fechaLimite() == null ? actual : otra;
    }

    private BigDecimal metaDe(int anio, int mes) {
        return metaPeriodoRepository.findById(new MetaPeriodoId((short) anio, (short) mes))
                .map(MetaPeriodo::getMetaPorcentaje)
                .orElse(META_POR_DEFECTO);
    }

    private Usuario usuarioActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        String nombre = autenticacion == null ? null : autenticacion.getName();
        if (nombre == null || nombre.isBlank()) {
            throw new ResourceNotFoundException("Usuario autenticado no encontrado");
        }
        return usuarioRepository.findByNombreUsuario(nombre)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));
    }

    private static void exigirPeriodo(int anio, int mes) {
        if (mes < 1 || mes > 12) {
            throw new BusinessException("El mes tiene que estar entre 1 y 12");
        }
        if (anio < 2000 || anio > 2100) {
            throw new BusinessException("El año no es válido");
        }
    }

    private static BigDecimal exigirMeta(BigDecimal metaPorcentaje) {
        if (metaPorcentaje == null) {
            throw new BusinessException("La meta tiene que estar entre 0 y 100");
        }
        BigDecimal valor = metaPorcentaje.setScale(2, RoundingMode.HALF_UP);
        if (valor.compareTo(BigDecimal.ZERO) < 0 || valor.compareTo(CIEN) > 0) {
            throw new BusinessException("La meta tiene que estar entre 0 y 100");
        }
        return valor;
    }

    static String exigirDescripcion(String descripcion) {
        String texto = descripcion == null ? "" : descripcion.trim();
        if (texto.isEmpty()) {
            throw new BusinessException("La descripción de la acción no puede ir vacía");
        }
        return texto;
    }

    private static EstadoAccionCorrectiva exigirEstado(String estado) {
        String codigo = estado == null ? "" : estado.trim();
        try {
            return EstadoAccionCorrectiva.valueOf(codigo);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("El estado tiene que ser PENDIENTE o HECHA");
        }
    }

    private static AccionCorrectivaResponse aRespuesta(AccionCorrectiva accion) {
        return new AccionCorrectivaResponse(accion.getIdAccion(), accion.getAnio(), accion.getMes(),
                accion.getDescripcion(), accion.getEstado().name(), accion.getFecha());
    }
}
