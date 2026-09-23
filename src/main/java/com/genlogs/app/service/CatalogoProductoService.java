package com.genlogs.app.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.CaracteristicaTecnicaResponse;
import com.genlogs.app.dto.CategoriaProductoResponse;
import com.genlogs.app.dto.MarcaResponse;
import com.genlogs.app.repository.CaracteristicaTecnicaRepository;
import com.genlogs.app.repository.CategoriaProductoRepository;
import com.genlogs.app.repository.MarcaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Consulta de los catálogos que alimentan los combos del formulario de
 * producto (categorías, marcas y características técnicas). Devuelve DTOs
 * para no exponer las entidades JPA (relaciones LAZY y campos de auditoría).
 */
@Service
@RequiredArgsConstructor
public class CatalogoProductoService {

    private final CategoriaProductoRepository categoriaProductoRepository;
    private final MarcaRepository marcaRepository;
    private final CaracteristicaTecnicaRepository caracteristicaTecnicaRepository;

    @Transactional(readOnly = true)
    public List<CategoriaProductoResponse> listarCategorias() {
        return categoriaProductoRepository.findByStatus("A").stream()
                .map(c -> CategoriaProductoResponse.builder()
                        .idCategoriaProducto(c.getIdCategoriaProducto())
                        .idCategoriaPadre(c.getCategoriaPadre() != null
                                ? c.getCategoriaPadre().getIdCategoriaProducto() : null)
                        .nombreCategoria(c.getNombreCategoria())
                        .slugWeb(c.getSlugWeb())
                        .nivel(c.getNivel())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MarcaResponse> listarMarcas() {
        return marcaRepository.findByStatus("A").stream()
                .map(m -> MarcaResponse.builder()
                        .idMarca(m.getIdMarca())
                        .nombreMarca(m.getNombreMarca())
                        .pais(m.getPais().getNombrePais())
                        .sitioWeb(m.getSitioWeb())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CaracteristicaTecnicaResponse> listarCaracteristicas() {
        return caracteristicaTecnicaRepository.findByStatus("A").stream()
                .map(c -> CaracteristicaTecnicaResponse.builder()
                        .idCaracteristica(c.getIdCaracteristica())
                        .nombreCaracteristica(c.getNombreCaracteristica())
                        .unidadCaracteristica(c.getUnidadCaracteristica())
                        .build())
                .collect(Collectors.toList());
    }
}
