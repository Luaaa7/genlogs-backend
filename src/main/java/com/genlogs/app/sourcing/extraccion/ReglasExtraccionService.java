package com.genlogs.app.sourcing.extraccion;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.fuentes.config.CampoSelector;
import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.FuenteSelector;
import com.genlogs.app.fuentes.config.FuenteSelectorRepository;
import com.genlogs.app.fuentes.config.PaginaSelector;

/**
 * Lee las reglas de extracción de fuente_selector en cada búsqueda (A4, v3.3).
 * No se guardan en memoria: si el Jefe Administrativo edita una regla (M4),
 * la siguiente búsqueda ya la usa, sin desplegar.
 */
@Service
public class ReglasExtraccionService {

    private static final String ACTIVA = "A";

    private final FuenteSelectorRepository fuenteSelectorRepository;

    public ReglasExtraccionService(FuenteSelectorRepository fuenteSelectorRepository) {
        this.fuenteSelectorRepository = fuenteSelectorRepository;
    }

    /** Reglas activas de la tienda; las vacías o en blanco se ignoran. */
    @Transactional(readOnly = true)
    public ReglasExtraccion reglasDe(CodigoFuente fuente) {
        Map<PaginaSelector, Map<CampoSelector, String>> reglas = new EnumMap<>(PaginaSelector.class);
        for (FuenteSelector s : fuenteSelectorRepository.findByFuente_CodigoAndStatus(fuente, ACTIVA)) {
            if (s.getPagina() == null || s.getCampo() == null) continue;
            if (s.getRegla() == null || s.getRegla().isBlank()) continue;
            reglas.computeIfAbsent(s.getPagina(), p -> new EnumMap<>(CampoSelector.class))
                    .put(s.getCampo(), s.getRegla().trim());
        }
        return new ReglasExtraccion(fuente, reglas);
    }
}