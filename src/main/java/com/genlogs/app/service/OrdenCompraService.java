package com.genlogs.app.service;

import com.genlogs.app.dto.OrdenCompraResponse;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.repository.OrdenCompraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrdenCompraService {

    private final OrdenCompraRepository ordenCompraRepository;

    @Transactional(readOnly = true)
    public OrdenCompraResponse buscarPorId(Long idOrdenCompra) {
        OrdenCompra oc = ordenCompraRepository.findById(idOrdenCompra)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de compra no encontrada"));
        return toResponse(oc);
    }

    @Transactional(readOnly = true)
    public OrdenCompraResponse buscarPorNumero(String numeroOrdenCompra) {
        OrdenCompra oc = ordenCompraRepository.findByNumeroOrdenCompra(numeroOrdenCompra)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de compra no encontrada"));
        return toResponse(oc);
    }

    @Transactional(readOnly = true)
    public List<OrdenCompraResponse> listarPorCliente(Long idCliente) {
        return ordenCompraRepository.findByCliente(idCliente)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private OrdenCompraResponse toResponse(OrdenCompra oc) {
        return OrdenCompraResponse.builder()
                .idOrdenCompra(oc.getIdOrdenCompra())
                .numeroOrdenCompra(oc.getNumeroOrdenCompra())
                .clienteRazonSocial(oc.getCotizacion().getCliente().getTercero().getRazonSocial())
                .fechaEmisionCliente(oc.getFechaEmisionCliente())
                .fechaRecepcion(oc.getFechaRecepcion())
                .estado(oc.getEstadoOrdenCompra().getNombreEstado())
                .build();
    }
}
