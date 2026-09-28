package com.genlogs.app.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.TipoComprobanteResponse;
import com.genlogs.app.repository.TipoComprobanteRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TipoComprobanteService {

    private final TipoComprobanteRepository tipoComprobanteRepository;

    @Transactional(readOnly = true)
    public List<TipoComprobanteResponse> listarTodos() {
        return tipoComprobanteRepository.findAll().stream()
                .map(t -> TipoComprobanteResponse.builder()
                        .idTipoComprobante(t.getIdTipoComprobante())
                        .codigoTipo(t.getCodigoTipo())
                        .nombreTipo(t.getNombreTipo())
                        .seriePrefijo(t.getSeriePrefijo())
                        .requiereRuc(t.getRequiereRuc())
                        .build())
                .toList();
    }
}
