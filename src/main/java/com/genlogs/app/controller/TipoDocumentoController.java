package com.genlogs.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genlogs.app.dto.TipoDocumentoResponse;
import com.genlogs.app.model.TipoDocumento;
import com.genlogs.app.repository.TipoDocumentoRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tipos-documento")
@RequiredArgsConstructor
public class TipoDocumentoController {

    private final TipoDocumentoRepository tipoDocumentoRepository;

    @GetMapping
    public ResponseEntity<List<TipoDocumentoResponse>> listar() {
        List<TipoDocumentoResponse> lista = tipoDocumentoRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(lista);
    }

    private TipoDocumentoResponse toResponse(TipoDocumento t) {
        return TipoDocumentoResponse.builder()
                .idTipoDocumento(t.getIdTipoDocumento())
                .codigoTipo(t.getCodigoTipo())
                .nombreTipo(t.getNombreTipo())
                .longitudDocumento(t.getLongitudDocumento())
                .soloNumerico(t.getSoloNumerico())
                .build();
    }
}