package com.genlogs.app.service;

import com.genlogs.app.dto.ClienteRequest;
import com.genlogs.app.dto.ClienteResponse;
// ⚠️ Estas dos excepciones las crea Luana en com.genlogs.app.exception
//    (núcleo). Si aún no existen, avísale para no bloquearte al compilar.
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.SectorEconomico; // la crea Mell
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.TipoDocumento;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.ProveedorRepository;
import com.genlogs.app.repository.SectorEconomicoRepository; // la crea Mell
import com.genlogs.app.repository.TerceroRepository;
import com.genlogs.app.repository.TipoDocumentoRepository;
import com.genlogs.app.repository.UbigeoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final ProveedorRepository proveedorRepository;
    private final TerceroRepository terceroRepository;
    private final TipoDocumentoRepository tipoDocumentoRepository;
    private final UbigeoRepository.DistritoRepository distritoRepository;
    private final SectorEconomicoRepository sectorEconomicoRepository; // repo de Mell

    /**
     * Da de alta un cliente. Si ya existe un Tercero con el mismo
     * (tipo_documento, numero_documento), reutiliza ese tercero
     * (por ejemplo, si ya era proveedor) en vez de duplicarlo.
     */
    @Transactional
    public ClienteResponse registrar(ClienteRequest request) {
        TipoDocumento tipoDocumento = tipoDocumentoRepository.findById(request.getIdTipoDocumento())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de documento no existe"));

        validarLongitudDocumento(tipoDocumento, request.getNumeroDocumento());

        Tercero tercero = terceroRepository
                .findByTipoDocumento_IdTipoDocumentoAndNumeroDocumento(
                        request.getIdTipoDocumento(), request.getNumeroDocumento())
                .orElseGet(() -> crearTercero(request, tipoDocumento));

        if (clienteRepository.findByTercero_IdTercero(tercero.getIdTercero()).isPresent()) {
            throw new BusinessException("Este documento ya está registrado como cliente");
        }

        SectorEconomico sector = sectorEconomicoRepository.findById(request.getIdSectorEconomico())
                .orElseThrow(() -> new ResourceNotFoundException("Sector económico no existe"));

        Cliente cliente = Cliente.builder()
                .tercero(tercero)
                .sectorEconomico(sector)
                .situacion("ACTIVO")
                .build();
        cliente.setUserCreate("SISTEMA");     // el filtro/interceptor de auditoría debería sobrescribir esto
        cliente.setProcessCreate("ALTA_CLIENTE");

        cliente = clienteRepository.save(cliente);
        return toResponse(cliente);
    }

    @Transactional(readOnly = true)
    public ClienteResponse buscarPorId(Long idCliente) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
        return toResponse(cliente);
    }

    @Transactional(readOnly = true)
    public ClienteResponse buscarPorDocumento(String numeroDocumento) {
        Cliente cliente = clienteRepository.findByNumeroDocumento(numeroDocumento)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
        return toResponse(cliente);
    }

    @Transactional(readOnly = true)
    public List<Cliente> listarActivos() {
        return clienteRepository.findBySituacionAndStatus("ACTIVO", "A");
    }

    @Transactional
    public void desactivar(Long idCliente) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
        cliente.setSituacion("INACTIVO");
        cliente.setStatus("I");
        clienteRepository.save(cliente);
    }

    // ------------------------------------------------------------------

    private Tercero crearTercero(ClienteRequest request, TipoDocumento tipoDocumento) {
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
        tercero.setProcessCreate("ALTA_CLIENTE");

        return terceroRepository.save(tercero);
    }

    /** Misma regla que valida el trigger fn_tercero_valida_documento() en la BD. */
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

    private ClienteResponse toResponse(Cliente cliente) {
        Tercero t = cliente.getTercero();
        boolean tambienProveedor = proveedorRepository
                .findByTercero_IdTercero(t.getIdTercero())
                .isPresent();

        return ClienteResponse.builder()
                .idCliente(cliente.getIdCliente())
                .idTercero(t.getIdTercero())
                .tipoDocumento(t.getTipoDocumento().getCodigoTipo())
                .numeroDocumento(t.getNumeroDocumento())
                .razonSocial(t.getRazonSocial())
                .direccion(t.getDireccion())
                .telefono(t.getTelefono())
                .correo(t.getCorreo())
                .sectorEconomico(cliente.getSectorEconomico() != null
                        ? cliente.getSectorEconomico().getNombreSector() : null)
                .situacion(cliente.getSituacion())
                .esTambienProveedor(tambienProveedor)
                .build();
    }
}