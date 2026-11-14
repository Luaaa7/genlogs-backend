package com.genlogs.app.fuentes.config;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * L5 (v3.3) · Configuración > Fuentes de búsqueda. Configura solo el Jefe Administrativo;
 * el Asistente Logístico solo ve el estado de cada tienda (matriz v3.3).
 */
@RestController
@RequestMapping("/api/fuentes")
@RequiredArgsConstructor
public class FuentesConfigController {

    private static final String SOLO_JEFE = "hasRole('JEFE_ADMINISTRATIVO')";

    private final FuentesConfigService service;
    private final SesionFuenteService sesiones;

    @GetMapping
    @PreAuthorize("hasAnyRole('JEFE_ADMINISTRATIVO','ASISTENTE_LOGISTICO')")
    public List<FuenteConfigResponse> listar() {
        return service.listar();
    }

    @PutMapping("/{codigo}/credenciales")
    @PreAuthorize(SOLO_JEFE)
    public ResponseEntity<Void> credenciales(@PathVariable String codigo,
                                             @RequestBody CredencialesFuenteRequest req) {
        service.guardarCredenciales(codigo, req);
        return ResponseEntity.noContent().build();
    }

    /** L1 · «Probar sesión» con la cuenta de búsqueda: ACTIVA, REQUIERE_VERIFICACION o CON_ERROR. */
    @PostMapping("/{codigo}/probar")
    @PreAuthorize(SOLO_JEFE)
    public FuenteConfigResponse probar(@PathVariable String codigo) {
        return sesiones.probar(codigo);
    }

    @PatchMapping("/{codigo}/activar")
    @PreAuthorize(SOLO_JEFE)
    public FuenteConfigResponse activar(@PathVariable String codigo, @Valid @RequestBody ActivarFuenteRequest req) {
        return service.activar(codigo, req.activa());
    }

    @PatchMapping("/{codigo}/parametros")
    @PreAuthorize(SOLO_JEFE)
    public FuenteConfigResponse parametros(@PathVariable String codigo,
                                           @Valid @RequestBody ParametrosFuenteRequest req) {
        return service.cambiarParametros(codigo, req);
    }

    @GetMapping("/{codigo}/selectores")
    @PreAuthorize(SOLO_JEFE)
    public List<SelectorFuenteDto> selectores(@PathVariable String codigo) {
        return service.listarSelectores(codigo);
    }

    @PutMapping("/{codigo}/selectores")
    @PreAuthorize(SOLO_JEFE)
    public ResponseEntity<Void> guardarSelectores(@PathVariable String codigo,
                                                  @RequestBody List<@Valid SelectorFuenteDto> reglas) {
        service.guardarSelectores(codigo, reglas);
        return ResponseEntity.noContent().build();
    }
}
