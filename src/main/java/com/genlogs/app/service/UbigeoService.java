package com.genlogs.app.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.model.Departamento;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.Pais;
import com.genlogs.app.model.Provincia;
import com.genlogs.app.repository.DepartamentoRepository;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.PaisRepository;
import com.genlogs.app.repository.ProvinciaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UbigeoService {

    private final PaisRepository paisRepository;
    private final DepartamentoRepository departamentoRepository;
    private final ProvinciaRepository provinciaRepository;
    private final DistritoRepository distritoRepository;

    @Transactional(readOnly = true)
    public List<Pais> listarPaises() {
        return paisRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Departamento> listarDepartamentos(Integer idPais) {
        return departamentoRepository.findByPais_IdPais(idPais);
    }

    @Transactional(readOnly = true)
    public List<Provincia> listarProvincias(Integer idDepartamento) {
        return provinciaRepository.findByDepartamento_IdDepartamento(idDepartamento);
    }

    @Transactional(readOnly = true)
    public List<Distrito> listarDistritos(Integer idProvincia) {
        return distritoRepository.findByProvincia_IdProvincia(idProvincia);
    }
}