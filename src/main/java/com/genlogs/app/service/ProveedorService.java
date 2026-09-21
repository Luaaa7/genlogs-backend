package com.genlogs.app.service;

import com.genlogs.app.dto.ProveedorRequest;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.Proveedor;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.TipoDocumento;
import com.genlogs.app.repository.ProveedorRepository;
import com.genlogs.app.repository.TerceroRepository;
import com.genlogs.app.repository.TipoDocumentoRepository;
import com.genlogs.app.repository.UbigeoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;
    private final TerceroRepository terceroRepository;
    private final TipoDocumentoRepository tipoDocumentoRepository;
    private final UbigeoRepository.DistritoRepository distritoRepository;

    /**
     * Da de alta un proveedor. Si ya existe un Tercero con el mismo
     * (tipo_documento, numero_documento) —por ejemplo porque ya es
     * cliente— reutiliza ese tercero en vez de duplicarlo.
     */
    @Transactional
    public Proveedor registrar(ProveedorRequest request) {
        TipoDocumento tipoDocumento = tipoDocumentoRepository.findById(request.getIdTipoDocumento())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de documento no existe"));

        validarLongitudDocumento(tipoDocumento, request.getNumeroDocumento());

        Tercero tercero = terceroRepository
                .findByTipoDocumento_IdTipoDocumentoAndNumeroDocumento(
                        request.getIdTipoDocumento(), request.getNumeroDocumento())
                .orElseGet(() -> crearTercero(request, tipoDocumento));

        if (proveedorRepository.findByTercero_IdTercero(tercero.getIdTercero()).isPresent()) {
            throw new BusinessException("Este documento ya está registrado como proveedor");
        }

        Proveedor proveedor = Proveedor.builder()
                .tercero(tercero)
                .situacion("ACTIVO")
                .build();
        proveedor.setUserCreate("SISTEMA");
        proveedor.setProcessCreate("ALTA_PROVEEDOR");

        return proveedorRepository.save(proveedor);
    }

    @Transactional(readOnly = true)
    public Proveedor buscarPorId(Long idProveedor) {
        return proveedorRepository.findById(idProveedor)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado"));
    }

    @Transactional(readOnly = true)
    public List<Proveedor> listarActivos() {
        return proveedorRepository.findBySituacionAndStatus("ACTIVO", "A");
    }

    @Transactional
    public void desactivar(Long idProveedor) {
        Proveedor proveedor = buscarPorId(idProveedor);
        proveedor.setSituacion("INACTIVO");
        proveedor.setStatus("I");
        proveedorRepository.save(proveedor);
    }

    // ------------------------------------------------------------------

    private Tercero crearTercero(ProveedorRequest request, TipoDocumento tipoDocumento) {
        Distrito distrito = distritoRepository.findById(request.getIdDistrito())
                .orElseThrow(() -> new ResourceNotFoundException("Distrito no existe"));

        Tercero tercero = Tercero.builder()
                .tipoDocumento(tipoDocumento)
                .distrito(distrito)
                .numeroDocumento(request.getNumeroDocumento())
                .razonSocial(request.getRazonSocial())
                .direccion(request.getDireccion())
                .telefono(request.getTelefono())
                .correo(request.getCorreo())
                .build();
        tercero.setUserCreate("SISTEMA");
        tercero.setProcessCreate("ALTA_PROVEEDOR");

        return terceroRepository.save(tercero);
    }

    private void validarLongitudDocumento(TipoDocumento tipoDocumento, String numeroDocumento) {
        if (numeroDocumento.length() != tipoDocumento.getLongitudDocumento()) {
            throw new BusinessException(String.format(
                    "El documento %s debe tener %d dígitos",
                    tipoDocumento.getCodigoTipo(), tipoDocumento.getLongitudDocumento()));
        }
        if (Boolean.TRUE.equals(tipoDocumento.getSoloNumerico()) && !numeroDocumento.matches("^[0-9]+$")) {
            throw new BusinessException(
                    "El documento " + tipoDocumento.getCodigoTipo() + " debe ser numérico");
        }
    }
}
