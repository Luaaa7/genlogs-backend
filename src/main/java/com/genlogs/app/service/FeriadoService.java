package com.genlogs.app.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Feriado;
import com.genlogs.app.repository.FeriadoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Feriados que no cuentan para los plazos de respuesta (D-18).
 * Solo se agregan o dan de baja fechas futuras: cambiar un feriado pasado
 * movería plazos que ya se calcularon y se mostraron.
 */
@Service
@RequiredArgsConstructor
public class FeriadoService {

    private static final String ACTIVO = "A";
    private static final String INACTIVO = "I";

    private final FeriadoRepository feriadoRepository;

    @Transactional(readOnly = true)
    public boolean esFeriado(LocalDate fecha) {
        return feriadoRepository.existsByFechaAndStatus(fecha, ACTIVO);
    }

    @Transactional(readOnly = true)
    public List<Feriado> listarDesde(LocalDate desde) {
        return feriadoRepository.findByStatusAndFechaGreaterThanEqualOrderByFechaAsc(
                ACTIVO, desde != null ? desde : LocalDate.now());
    }

    @Transactional
    public Feriado crear(LocalDate fecha, String descripcion) {
        if (fecha == null) {
            throw new BusinessException("Indica la fecha del feriado");
        }
        if (!fecha.isAfter(LocalDate.now())) {
            throw new BusinessException("Solo se pueden agregar feriados de fechas futuras");
        }
        String texto = descripcion == null ? "" : descripcion.trim();
        if (texto.isEmpty()) {
            throw new BusinessException("Indica el motivo del feriado");
        }
        if (texto.length() > 100) {
            throw new BusinessException("El motivo no puede pasar de 100 caracteres");
        }
        if (feriadoRepository.existsByFechaAndStatus(fecha, ACTIVO)) {
            throw new BusinessException("Ya hay un feriado registrado el " + fecha);
        }
        Feriado feriado = new Feriado();
        feriado.setFecha(fecha);
        feriado.setDescripcion(texto);
        feriado.setUserCreate(usuarioActual());
        feriado.setProcessCreate("FeriadoService.crear");
        feriado.setDateCreate(LocalDateTime.now());
        return feriadoRepository.save(feriado);
    }

    @Transactional
    public void darDeBaja(Integer idFeriado) {
        Feriado feriado = feriadoRepository.findById(idFeriado)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el feriado " + idFeriado));
        if (!feriado.getFecha().isAfter(LocalDate.now())) {
            throw new BusinessException("No se puede dar de baja un feriado que ya pasó: ya se usó para calcular plazos");
        }
        feriado.setStatus(INACTIVO);
        feriado.setUserUpdate(usuarioActual());
        feriado.setProcessUpdate("FeriadoService.darDeBaja");
        feriado.setDateUpdate(LocalDateTime.now());
        feriadoRepository.save(feriado);
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "sistema";
    }
}
