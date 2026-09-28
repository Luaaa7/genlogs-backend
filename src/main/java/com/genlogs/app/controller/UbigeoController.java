package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.DepartamentoResponse;
import com.genlogs.app.dto.DistritoResponse;
import com.genlogs.app.dto.PaisResponse;
import com.genlogs.app.dto.ProvinciaResponse;
import com.genlogs.app.model.Departamento;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.Pais;
import com.genlogs.app.model.Provincia;
import com.genlogs.app.repository.PaisRepository;
import com.genlogs.app.repository.DepartamentoRepository;
import com.genlogs.app.repository.ProvinciaRepository;
import com.genlogs.app.repository.DistritoRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UbigeoController {

    private final PaisRepository paisRepository;
    private final DepartamentoRepository departamentoRepository;
    private final ProvinciaRepository provinciaRepository;
    private final DistritoRepository distritoRepository;

    @GetMapping("/paises")
    public ResponseEntity<List<PaisResponse>> listarPaises() {
        List<PaisResponse> lista = paisRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/departamentos")
    public ResponseEntity<List<DepartamentoResponse>> listarDepartamentos(
            @RequestParam Integer idPais) {
        List<DepartamentoResponse> lista = departamentoRepository.findByPais_IdPais(idPais).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/provincias")
    public ResponseEntity<List<ProvinciaResponse>> listarProvincias(
            @RequestParam Integer idDepartamento) {
        List<ProvinciaResponse> lista = provinciaRepository.findByDepartamento_IdDepartamento(idDepartamento).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/distritos")
    public ResponseEntity<List<DistritoResponse>> listarDistritos(
            @RequestParam Integer idProvincia) {
        List<DistritoResponse> lista = distritoRepository.findByProvincia_IdProvincia(idProvincia).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(lista);
    }

    private PaisResponse toResponse(Pais p) {
        return PaisResponse.builder()
                .idPais(p.getIdPais())
                .codigoIso(p.getCodigoIso())
                .nombrePais(p.getNombrePais())
                .build();
    }

    private DepartamentoResponse toResponse(Departamento d) {
        return DepartamentoResponse.builder()
                .idDepartamento(d.getIdDepartamento())
                .nombreDepartamento(d.getNombreDepartamento())
                .build();
    }

    private ProvinciaResponse toResponse(Provincia p) {
        return ProvinciaResponse.builder()
                .idProvincia(p.getIdProvincia())
                .nombreProvincia(p.getNombreProvincia())
                .build();
    }

    private DistritoResponse toResponse(Distrito d) {
        return DistritoResponse.builder()
                .idDistrito(d.getIdDistrito())
                .nombreDistrito(d.getNombreDistrito())
                .ubigeo(d.getUbigeo())
                .build();
    }
}