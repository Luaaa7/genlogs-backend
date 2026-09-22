package com.genlogs.app.controller;

import com.genlogs.app.model.EmpresaMineraMineral;
import com.genlogs.app.service.EmpresaMineraMineralService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Rutas anidadas bajo /api/empresas-mineras/{idEmpresaMinera}/minerales */
@RestController
@RequestMapping("/api/empresas-mineras/{idEmpresaMinera}/minerales")
@RequiredArgsConstructor
public class EmpresaMineraMineralController {

    private final EmpresaMineraMineralService empresaMineraMineralService;

    @GetMapping
    public ResponseEntity<List<EmpresaMineraMineral>> listar(@PathVariable Long idEmpresaMinera) {
        return ResponseEntity.ok(empresaMineraMineralService.listarPorEmpresaMinera(idEmpresaMinera));
    }

    @PostMapping("/{idMineral}")
    public ResponseEntity<EmpresaMineraMineral> asociar(@PathVariable Long idEmpresaMinera,
                                                         @PathVariable Integer idMineral,
                                                         @RequestParam(defaultValue = "false") Boolean esPrincipal) {
        EmpresaMineraMineral creada = empresaMineraMineralService.asociar(idEmpresaMinera, idMineral, esPrincipal);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @DeleteMapping("/{idMineral}")
    public ResponseEntity<Void> desasociar(@PathVariable Long idEmpresaMinera, @PathVariable Integer idMineral) {
        empresaMineraMineralService.desasociar(idEmpresaMinera, idMineral);
        return ResponseEntity.noContent().build();
    }
}
