package com.genlogs.app.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.ProveedorRequest;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.Proveedor;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.TipoDocumento;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.ProveedorRepository;
import com.genlogs.app.repository.TerceroRepository;
import com.genlogs.app.repository.TipoDocumentoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;
    private final TerceroRepository terceroRepository;
    private final TipoDocumentoRepository tipoDocumentoRepository;
    private final DistritoRepository distritoRepository;

    /**
     * Registra un proveedor.
     *
     * Si ya existe un Tercero con el mismo tipo y número de documento,
     * reutiliza ese registro para evitar duplicarlo.
     *
     * Si el tercero todavía no existe, lo crea junto con el proveedor.
     */
    @Transactional
    public Proveedor registrar(ProveedorRequest request) {
        if (request == null) {
            throw new BusinessException(
                    "Los datos del proveedor son obligatorios");
        }

        if (request.getIdTipoDocumento() == null) {
            throw new BusinessException(
                    "Debe seleccionar un tipo de documento");
        }

        String numeroDocumento = request.getNumeroDocumento();

        if (numeroDocumento == null || numeroDocumento.isBlank()) {
            throw new BusinessException(
                    "El número de documento es obligatorio");
        }

        numeroDocumento = numeroDocumento.trim();

        TipoDocumento tipoDocumento = tipoDocumentoRepository
                .findById(request.getIdTipoDocumento())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tipo de documento no existe"));

        validarLongitudDocumento(tipoDocumento, numeroDocumento);

        final String documentoValidado = numeroDocumento;

        Tercero tercero = terceroRepository
                .findByTipoDocumento_IdTipoDocumentoAndNumeroDocumento(
                        request.getIdTipoDocumento(),
                        documentoValidado)
                .orElseGet(() -> crearTercero(
                        request,
                        tipoDocumento,
                        documentoValidado));

        if (proveedorRepository
                .findByTercero_IdTercero(tercero.getIdTercero())
                .isPresent()) {
            throw new BusinessException(
                    "Este documento ya está registrado como proveedor");
        }

        Proveedor proveedor = Proveedor.builder()
                .tercero(tercero)
                .situacion("ACTIVO")
                .build();

        proveedor.setUserCreate(usuarioActual());
        proveedor.setProcessCreate("ALTA_PROVEEDOR");

        return proveedorRepository.save(proveedor);
    }

    /**
     * Edita los datos del tercero del proveedor (documento, razón social,
     * distrito, dirección, teléfono, correo). El tercero es el mismo para el
     * rol de cliente, así que el cambio también se ve allí.
     */
    @Transactional
    public Proveedor actualizar(Long idProveedor, ProveedorRequest request) {
        Proveedor proveedor = buscarPorId(idProveedor);

        if (request == null || request.getIdTipoDocumento() == null) {
            throw new BusinessException("Debe seleccionar un tipo de documento");
        }
        if (request.getNumeroDocumento() == null || request.getNumeroDocumento().isBlank()) {
            throw new BusinessException("El número de documento es obligatorio");
        }
        if (request.getIdDistrito() == null) {
            throw new BusinessException("Debe seleccionar un distrito para el proveedor");
        }

        TipoDocumento tipoDocumento = tipoDocumentoRepository
                .findById(request.getIdTipoDocumento())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de documento no existe"));
        String numeroDocumento = request.getNumeroDocumento().trim();
        validarLongitudDocumento(tipoDocumento, numeroDocumento);

        Tercero tercero = proveedor.getTercero();

        // Si cambió el documento, no puede ser el de otro tercero ya registrado.
        terceroRepository
                .findByTipoDocumento_IdTipoDocumentoAndNumeroDocumento(request.getIdTipoDocumento(), numeroDocumento)
                .filter(otro -> !otro.getIdTercero().equals(tercero.getIdTercero()))
                .ifPresent(otro -> {
                    throw new BusinessException("Ya existe otro cliente o proveedor con ese documento");
                });

        Distrito distrito = distritoRepository
                .findById(request.getIdDistrito())
                .orElseThrow(() -> new ResourceNotFoundException("Distrito no existe"));

        tercero.setTipoDocumento(tipoDocumento);
        tercero.setNumeroDocumento(numeroDocumento);
        tercero.setRazonSocial(request.getRazonSocial() != null ? request.getRazonSocial().trim() : tercero.getRazonSocial());
        tercero.setDistrito(distrito);
        tercero.setDireccion(request.getDireccion());
        tercero.setTelefono(request.getTelefono());
        tercero.setCorreo(request.getCorreo());
        tercero.setUserUpdate(usuarioActual());
        tercero.setProcessUpdate("EDITAR_PROVEEDOR");
        tercero.setDateUpdate(LocalDateTime.now());
        terceroRepository.save(tercero);

        proveedor.setUserUpdate(usuarioActual());
        proveedor.setProcessUpdate("EDITAR_PROVEEDOR");
        proveedor.setDateUpdate(LocalDateTime.now());
        return proveedorRepository.save(proveedor);
    }

    @Transactional(readOnly = true)
    public Proveedor buscarPorId(Long idProveedor) {
        if (idProveedor == null) {
            throw new BusinessException(
                    "El identificador del proveedor es obligatorio");
        }

        return proveedorRepository.findById(idProveedor)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Proveedor no encontrado"));
    }

    @Transactional(readOnly = true)
    public List<Proveedor> listarActivos() {
        return proveedorRepository.findBySituacionAndStatus(
                "ACTIVO",
                "A");
    }

    @Transactional
    public void desactivar(Long idProveedor) {
        Proveedor proveedor = buscarPorId(idProveedor);

        if ("INACTIVO".equalsIgnoreCase(proveedor.getSituacion())
                || "I".equalsIgnoreCase(proveedor.getStatus())) {
            throw new BusinessException(
                    "El proveedor ya se encuentra inactivo");
        }

        proveedor.setSituacion("INACTIVO");
        proveedor.setStatus("I");
        proveedor.setUserUpdate(usuarioActual());
        proveedor.setProcessUpdate("BAJA_PROVEEDOR");
        proveedor.setDateUpdate(LocalDateTime.now());

        proveedorRepository.save(proveedor);
    }

    /**
     * Crea un tercero únicamente cuando no existe uno con el mismo
     * tipo y número de documento.
     */
    private Tercero crearTercero(
            ProveedorRequest request,
            TipoDocumento tipoDocumento,
            String numeroDocumento) {

        if (request.getIdDistrito() == null) {
            throw new BusinessException(
                    "Debe seleccionar un distrito para el proveedor");
        }

        Distrito distrito = distritoRepository
                .findById(request.getIdDistrito())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Distrito no existe"));

        Tercero tercero = Tercero.builder()
                .tipoDocumento(tipoDocumento)
                .distrito(distrito)
                .numeroDocumento(numeroDocumento)
                .razonSocial(request.getRazonSocial())
                .direccion(request.getDireccion())
                .telefono(request.getTelefono())
                .correo(request.getCorreo())
                .build();

        tercero.setUserCreate(usuarioActual());
        tercero.setProcessCreate("ALTA_PROVEEDOR");

        return terceroRepository.save(tercero);
    }

    /**
     * Valida el formato del documento según el tipo seleccionado.
     */
    private void validarLongitudDocumento(
            TipoDocumento tipoDocumento,
            String numeroDocumento) {

        if (tipoDocumento.getLongitudDocumento() == null
                || tipoDocumento.getLongitudDocumento() <= 0) {
            throw new BusinessException(
                    "El tipo de documento tiene una longitud configurada inválida");
        }

        if (numeroDocumento.length()
                != tipoDocumento.getLongitudDocumento()) {
            throw new BusinessException(String.format(
                    "El documento %s debe tener %d caracteres",
                    tipoDocumento.getCodigoTipo(),
                    tipoDocumento.getLongitudDocumento()));
        }

        if (Boolean.TRUE.equals(tipoDocumento.getSoloNumerico())
                && !numeroDocumento.matches("^[0-9]+$")) {
            throw new BusinessException(
                    "El documento "
                            + tipoDocumento.getCodigoTipo()
                            + " debe ser numérico");
        }
    }

    /**
     * Obtiene el nombre del usuario autenticado.
     * Evita registrar operaciones sin un usuario válido en el contexto
     * de seguridad.
     */
    private String usuarioActual() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()
                || "anonymousUser".equals(authentication.getName())) {
            throw new AuthenticationCredentialsNotFoundException(
                    "No hay un usuario autenticado para registrar la operación");
        }

        return authentication.getName();
    }
}