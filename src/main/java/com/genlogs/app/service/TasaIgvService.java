package com.genlogs.app.service;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.TasaIgv;
import com.genlogs.app.repository.TasaIgvRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Tasa del IGV configurable (antes estaba fija en 18% en varios lugares). */
@Service
@RequiredArgsConstructor
public class TasaIgvService {

    /** Si la tabla aún no tiene datos, se usa la tasa vigente en el Perú desde 2011. */
    public static final BigDecimal PORCENTAJE_POR_DEFECTO = new BigDecimal("18.00");

    private final TasaIgvRepository tasaIgvRepository;

    /** Porcentaje que rige hoy (por ejemplo 18.00). */
    @Transactional(readOnly = true)
    public BigDecimal porcentajeVigente() {
        return porcentajeVigente(LocalDate.now());
    }

    /** Porcentaje que rige en una fecha. */
    @Transactional(readOnly = true)
    public BigDecimal porcentajeVigente(LocalDate fecha) {
        return tasaIgvRepository
                .findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(fecha != null ? fecha : LocalDate.now())
                .map(TasaIgv::getPorcentaje)
                .orElse(PORCENTAJE_POR_DEFECTO);
    }

    @Transactional(readOnly = true)
    public List<TasaIgv> historial() {
        return tasaIgvRepository.findAllByOrderByVigenteDesdeDesc();
    }

    /** Registra una nueva tasa (solo ADMINISTRADOR, ver TasaIgvController). */
    @Transactional
    public TasaIgv registrar(BigDecimal porcentaje, LocalDate vigenteDesde) {
        if (porcentaje == null || porcentaje.compareTo(BigDecimal.ZERO) < 0 || porcentaje.compareTo(new BigDecimal("100")) >= 0) {
            throw new BusinessException("El porcentaje del IGV debe estar entre 0 y 99.99");
        }
        if (vigenteDesde == null) {
            throw new BusinessException("Indica desde qué fecha rige la nueva tasa");
        }
        if (tasaIgvRepository.existsByVigenteDesde(vigenteDesde)) {
            throw new BusinessException("Ya hay una tasa registrada para esa fecha");
        }
        TasaIgv tasa = TasaIgv.builder()
                .porcentaje(porcentaje.setScale(2, RoundingMode.HALF_UP))
                .vigenteDesde(vigenteDesde)
                .userCreate(SecurityContextHolder.getContext().getAuthentication().getName())
                .dateCreate(LocalDateTime.now())
                .build();
        return tasaIgvRepository.save(tasa);
    }

    /** 18.00 -> 0.18 */
    public static BigDecimal comoFactor(BigDecimal porcentaje) {
        BigDecimal p = porcentaje != null ? porcentaje : PORCENTAJE_POR_DEFECTO;
        return p.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
    }

    /** 18.00 -> "18%", 18.50 -> "18.5%" */
    public static String comoTexto(BigDecimal porcentaje) {
        BigDecimal p = porcentaje != null ? porcentaje : PORCENTAJE_POR_DEFECTO;
        return p.stripTrailingZeros().toPlainString() + "%";
    }
}