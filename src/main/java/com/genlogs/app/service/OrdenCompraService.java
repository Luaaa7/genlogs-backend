package com.genlogs.app.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.genlogs.app.dto.CambiarEstadoOrdenCompraRequest;
import com.genlogs.app.dto.OrdenCompraRequest;
import com.genlogs.app.dto.OrdenCompraResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.CondicionPago;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.EstadoOrdenCompra;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CondicionPagoRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.EstadoOrdenCompraRepository;
import com.genlogs.app.repository.OrdenCompraRepository;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrdenCompraService {

    private final OrdenCompraRepository ordenCompraRepository;
    private final CotizacionRepository cotizacionRepository;
    private final EstadoOrdenCompraRepository estadoOrdenCompraRepository;
    private final CondicionPagoRepository condicionPagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CloudinaryService cloudinaryService;

    @Transactional(readOnly = true)
    public OrdenCompraResponse buscarPorId(Long idOrdenCompra) {
        return toResponse(obtenerOLanzar(idOrdenCompra));
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

    @Transactional
    public OrdenCompraResponse crear(OrdenCompraRequest request) {
        Cotizacion cotizacion = cotizacionRepository.findById(request.getIdCotizacion())
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));

        if (!"APROBADA".equals(cotizacion.getEstadoCotizacion().getCodigoEstado())) {
            throw new BusinessException("Solo se puede generar una orden de compra sobre una cotización APROBADA");
        }

        if (ordenCompraRepository.existsByCotizacion_IdCotizacionAndStatus(cotizacion.getIdCotizacion(), "A")) {
            throw new BusinessException("Esta cotización ya tiene una orden de compra registrada");
        }

        CondicionPago condicionPago = request.getIdCondicionPago() != null
                ? condicionPagoRepository.findById(request.getIdCondicionPago())
                        .orElseThrow(() -> new ResourceNotFoundException("Condición de pago no encontrada"))
                : cotizacion.getCondicionPago();

        EstadoOrdenCompra estadoInicial = estadoOrdenCompraRepository.findByCodigoEstado("RECIBIDA")
                .orElseThrow(() -> new BusinessException("No existe el estado RECIBIDA configurado"));

        Usuario usuario = usuarioActual();

        OrdenCompra ordenCompra = OrdenCompra.builder()
                .cotizacion(cotizacion)
                .estadoOrdenCompra(estadoInicial)
                .condicionPago(condicionPago)
                .numeroOrdenCompra(request.getNumeroOrdenCompra())
                .fechaEmisionCliente(request.getFechaEmisionCliente())
                .observaciones(request.getObservaciones())
                .build();
        ordenCompra.setUserCreate(usuario.getNombreUsuario());
        ordenCompra.setProcessCreate("ALTA_ORDEN_COMPRA");

        ordenCompra = ordenCompraRepository.save(ordenCompra);

        return toResponse(ordenCompra);
    }

    @Transactional
    public OrdenCompraResponse cambiarEstado(Long idOrdenCompra, CambiarEstadoOrdenCompraRequest request) {
        OrdenCompra ordenCompra = obtenerOLanzar(idOrdenCompra);

        EstadoOrdenCompra nuevoEstado = estadoOrdenCompraRepository.findByCodigoEstado(request.getCodigoEstado())
                .orElseThrow(() -> new ResourceNotFoundException("Estado no encontrado: " + request.getCodigoEstado()));

        if (Boolean.TRUE.equals(ordenCompra.getEstadoOrdenCompra().getEsFinal())) {
            throw new BusinessException("No se puede cambiar el estado de una orden de compra ya finalizada");
        }

        ordenCompra.setEstadoOrdenCompra(nuevoEstado);
        if (request.getObservaciones() != null) {
            ordenCompra.setObservaciones(request.getObservaciones());
        }

        Usuario usuario = usuarioActual();
        ordenCompra.setUserUpdate(usuario.getNombreUsuario());
        ordenCompra.setProcessUpdate("CAMBIO_ESTADO_ORDEN_COMPRA");
        ordenCompra.setDateUpdate(LocalDateTime.now());

        ordenCompra = ordenCompraRepository.save(ordenCompra);
        return toResponse(ordenCompra);
    }

    @Transactional
    public OrdenCompraResponse agregarArchivo(Long idOrdenCompra, MultipartFile archivo) {
        OrdenCompra ordenCompra = obtenerOLanzar(idOrdenCompra);

        String url = cloudinaryService.subirDocumento(archivo, "genlogs/ordenes-compra");
        ordenCompra.setUrlArchivo(url);

        Usuario usuario = usuarioActual();
        ordenCompra.setUserUpdate(usuario.getNombreUsuario());
        ordenCompra.setProcessUpdate("ADJUNTAR_ARCHIVO_ORDEN_COMPRA");
        ordenCompra.setDateUpdate(LocalDateTime.now());

        ordenCompra = ordenCompraRepository.save(ordenCompra);
        return toResponse(ordenCompra);
    }

    // ------------------------------------------------------------------

    private OrdenCompra obtenerOLanzar(Long idOrdenCompra) {
        return ordenCompraRepository.findById(idOrdenCompra)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de compra no encontrada"));
    }

    private Usuario usuarioActual() {
        String nombreUsuario = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));
    }

    private OrdenCompraResponse toResponse(OrdenCompra oc) {
        return OrdenCompraResponse.builder()
                .idOrdenCompra(oc.getIdOrdenCompra())
                .idCotizacion(oc.getCotizacion().getIdCotizacion())
                .codigoCotizacion(oc.getCotizacion().getCodigoCotizacion())
                .numeroOrdenCompra(oc.getNumeroOrdenCompra())
                .clienteRazonSocial(oc.getCotizacion().getCliente().getTercero().getRazonSocial())
                .fechaEmisionCliente(oc.getFechaEmisionCliente())
                .fechaRecepcion(oc.getFechaRecepcion())
                .estado(oc.getEstadoOrdenCompra().getNombreEstado())
                .urlArchivo(oc.getUrlArchivo())
                .observaciones(oc.getObservaciones())
                .build();
    }
}
