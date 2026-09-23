package com.genlogs.app.service;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.EmpresaMinera;
import com.genlogs.app.model.EtapaComercialMinera;
import com.genlogs.app.model.TipoOperacionMinera;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.EmpresaMineraRepository;
import com.genlogs.app.repository.EtapaComercialMineraRepository;
import com.genlogs.app.repository.TipoOperacionMineraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmpresaMineraService {

    private final EmpresaMineraRepository empresaMineraRepository;
    private final ClienteRepository clienteRepository;
    private final EtapaComercialMineraRepository etapaComercialMineraRepository;
    private final TipoOperacionMineraRepository tipoOperacionMineraRepository;

    @Transactional(readOnly = true)
    public List<EmpresaMinera> listarTodos() {
        return empresaMineraRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<EmpresaMinera> listarActivos() {
        return empresaMineraRepository.findByStatus("A");
    }

    @Transactional(readOnly = true)
    public EmpresaMinera buscarPorId(Long id) {
        return empresaMineraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa minera no encontrada"));
    }

    @Transactional(readOnly = true)
    public EmpresaMinera buscarPorCliente(Long idCliente) {
        return empresaMineraRepository.findByCliente_IdCliente(idCliente)
                .orElseThrow(() -> new ResourceNotFoundException("Este cliente no tiene ficha de empresa minera"));
    }

    @Transactional
    public EmpresaMinera registrar(EmpresaMinera nueva) {
        Cliente cliente = resolverCliente(nueva.getCliente());
        if (empresaMineraRepository.existsByCliente_IdCliente(cliente.getIdCliente())) {
            throw new BusinessException("Este cliente ya tiene una ficha de empresa minera registrada");
        }

        nueva.setCliente(cliente);
        nueva.setEtapaComercial(resolverEtapa(nueva.getEtapaComercial()));
        nueva.setTipoOperacion(resolverTipoOperacion(nueva.getTipoOperacion()));
        nueva.setStatus("A");
        nueva.setUserCreate(usuarioActual());
        nueva.setProcessCreate("ALTA_EMPRESA_MINERA");
        nueva.setDateCreate(LocalDateTime.now());
        return empresaMineraRepository.save(nueva);
    }

    @Transactional
    public EmpresaMinera actualizar(Long id, EmpresaMinera datos) {
        EmpresaMinera existente = buscarPorId(id);
        existente.setEtapaComercial(resolverEtapa(datos.getEtapaComercial()));
        existente.setTipoOperacion(resolverTipoOperacion(datos.getTipoOperacion()));
        existente.setNombreUnidadMinera(datos.getNombreUnidadMinera());
        existente.setLatitud(datos.getLatitud());
        existente.setLongitud(datos.getLongitud());
        existente.setAltitudMsnm(datos.getAltitudMsnm());
        existente.setCapacidadProduccion(datos.getCapacidadProduccion());
        existente.setDescripcion(datos.getDescripcion());
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("EDICION_EMPRESA_MINERA");
        existente.setDateUpdate(LocalDateTime.now());
        return empresaMineraRepository.save(existente);
    }

    @Transactional
    public void desactivar(Long id) {
        EmpresaMinera existente = buscarPorId(id);
        existente.setStatus("I");
        existente.setUserUpdate(usuarioActual());
        existente.setProcessUpdate("BAJA_EMPRESA_MINERA");
        existente.setDateUpdate(LocalDateTime.now());
        empresaMineraRepository.save(existente);
    }

    // ------------------------------------------------------------------

    private Cliente resolverCliente(Cliente ref) {
        if (ref == null || ref.getIdCliente() == null) {
            throw new BusinessException("Debe indicar el cliente dueño de la unidad minera");
        }
        return clienteRepository.findById(ref.getIdCliente())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no existe"));
    }

    private EtapaComercialMinera resolverEtapa(EtapaComercialMinera ref) {
        if (ref == null || ref.getIdEtapaComercial() == null) {
            throw new BusinessException("Debe indicar la etapa comercial");
        }
        return etapaComercialMineraRepository.findById(ref.getIdEtapaComercial())
                .orElseThrow(() -> new ResourceNotFoundException("Etapa comercial no existe"));
    }

    private TipoOperacionMinera resolverTipoOperacion(TipoOperacionMinera ref) {
        if (ref == null || ref.getIdTipoOperacion() == null) {
            throw new BusinessException("Debe indicar el tipo de operación");
        }
        return tipoOperacionMineraRepository.findById(ref.getIdTipoOperacion())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de operación no existe"));
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
