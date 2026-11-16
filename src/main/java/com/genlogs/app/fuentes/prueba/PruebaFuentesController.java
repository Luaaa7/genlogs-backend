package com.genlogs.app.fuentes.prueba;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.sourcing.ConsultaFuente;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * M2 (v3.3, BPMN 9-C) · Prueba de 10 pedidos. La hace solo el Jefe Administrativo,
 * desde la pantalla Búsqueda de prueba (M3).
 */
@RestController
@RequestMapping("/api/fuentes/prueba")
@RequiredArgsConstructor
public class PruebaFuentesController {

    private static final String SOLO_JEFE = "hasRole('JEFE_ADMINISTRATIVO')";

    private final PruebaFuentesService service;

    /** Tabla de aciertos sobre 10: la última corrida de cada tienda. */
    @GetMapping
    @PreAuthorize(SOLO_JEFE)
    public List<ResumenPruebaResponse> resumen() {
        return service.resumen();
    }

    /** Busca un pedido en las 5 tiendas sin guardar nada en el historial. */
    @PostMapping("/buscar")
    @PreAuthorize(SOLO_JEFE)
    public List<ConsultaFuente> buscar(@Valid @RequestBody BusquedaPruebaRequest req) {
        return service.buscar(req);
    }

    /** Guarda lo que marcó el Jefe y deja los aciertos de cada tienda. */
    @PostMapping("/corridas")
    @PreAuthorize(SOLO_JEFE)
    public List<CorridaPruebaResponse> guardarCorrida(@Valid @RequestBody CorridaPruebaRequest req) {
        return service.guardarCorrida(req);
    }
}