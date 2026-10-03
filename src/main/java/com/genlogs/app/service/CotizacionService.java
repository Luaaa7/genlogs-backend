package com.genlogs.app.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.genlogs.app.dto.AdjuntoCotizacionResponse;
import com.genlogs.app.dto.CambiarEstadoCotizacionRequest;
import com.genlogs.app.dto.CotizacionDetalleRequest;
import com.genlogs.app.dto.CotizacionDetalleResponse;
import com.genlogs.app.dto.CotizacionRequest;
import com.genlogs.app.dto.CotizacionResponse;
import com.genlogs.app.dto.PaginaResponse;
import com.genlogs.app.dto.SeguimientoCotizacionResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.AdjuntoCotizacion;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.CondicionPago;
import com.genlogs.app.model.ContactoTercero;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.CotizacionDetalle;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.SectorEconomico;
import com.genlogs.app.model.SeguimientoCotizacion;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.AdjuntoCotizacionRepository;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.CondicionPagoRepository;
import com.genlogs.app.repository.ContactoTerceroRepository;
import com.genlogs.app.repository.CotizacionDetalleRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.CotizacionRepository.TotalesCotizacionProjection;
import com.genlogs.app.repository.EstadoCotizacionRepository;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.SectorEconomicoRepository;
import com.genlogs.app.repository.SeguimientoCotizacionRepository;
import com.genlogs.app.repository.ServicioRepository;
import com.genlogs.app.repository.UnidadMedidaRepository;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CotizacionService {

    private final CotizacionRepository cotizacionRepository;
    private final CotizacionDetalleRepository cotizacionDetalleRepository;
    private final EstadoCotizacionRepository estadoCotizacionRepository;
    private final SeguimientoCotizacionRepository seguimientoCotizacionRepository;
    private final AdjuntoCotizacionRepository adjuntoCotizacionRepository;
    private final ClienteRepository clienteRepository;
    private final ContactoTerceroRepository contactoTerceroRepository;
    private final UsuarioRepository usuarioRepository;
    private final MonedaRepository monedaRepository;
    private final CondicionPagoRepository condicionPagoRepository;
    private final SectorEconomicoRepository sectorEconomicoRepository;
    private final ProductoRepository productoRepository;
    private final ServicioRepository servicioRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;
    private final CloudinaryService cloudinaryService;

    @Transactional(readOnly = true)
    public CotizacionResponse buscarPorId(Long idCotizacion) {
        return toResponse(obtenerOLanzar(idCotizacion));
    }

    @Transactional(readOnly = true)
    public CotizacionResponse buscarPorCodigo(String codigoCotizacion) {
        Cotizacion c = cotizacionRepository.findByCodigoCotizacion(codigoCotizacion)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));
        return toResponse(c);
    }

    /**
     * Listado paginado con filtros opcionales, para CotizacionesListPage.tsx.
     */
    @Transactional(readOnly = true)
    public PaginaResponse<CotizacionResponse> listar(String codigoEstado, Long idCliente, String codigoMoneda, Pageable pageable) {
        Page<Cotizacion> pagina = cotizacionRepository.buscarPaginado(codigoEstado, idCliente, codigoMoneda, pageable);
        return PaginaResponse.of(pagina.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<CotizacionResponse> listarPorCliente(Long idCliente) {
        return cotizacionRepository.findByCliente(idCliente).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CotizacionDetalleResponse> listarLineas(Long idCotizacion) {
        return cotizacionDetalleRepository.findByCotizacion(idCotizacion).stream()
                .map(this::toLineaResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SeguimientoCotizacionResponse> listarSeguimiento(Long idCotizacion) {
        return seguimientoCotizacionRepository.findByCotizacion(idCotizacion).stream()
                .map(s -> SeguimientoCotizacionResponse.builder()
                        .idSeguimiento(s.getIdSeguimiento())
                        .estado(s.getEstadoCotizacion().getNombreEstado())
                        .usuario(s.getUsuario() != null ? s.getUsuario().getNombreUsuario() : null)
                        .fechaEvento(s.getFechaEvento())
                        .comentario(s.getComentario())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdjuntoCotizacionResponse> listarAdjuntos(Long idCotizacion) {
        return adjuntoCotizacionRepository.findByCotizacion(idCotizacion).stream()
                .map(a -> AdjuntoCotizacionResponse.builder()
                        .idAdjunto(a.getIdAdjunto())
                        .nombreArchivo(a.getNombreArchivo())
                        .urlArchivo(a.getUrlArchivo())
                        .build())
                .toList();
    }

    @Transactional
    public CotizacionResponse crear(CotizacionRequest request) {
        Cliente cliente = clienteRepository.findById(request.getIdCliente())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));

        ContactoTercero contacto = null;
        if (request.getIdContacto() != null) {
            contacto = contactoTerceroRepository.findById(request.getIdContacto())
                    .orElseThrow(() -> new ResourceNotFoundException("Contacto no encontrado"));

            if (!contacto.getTercero().getIdTercero().equals(cliente.getTercero().getIdTercero())) {
                throw new BusinessException("El contacto no pertenece a este cliente");
            }
        }

        Moneda moneda = monedaRepository.findById(request.getIdMoneda())
                .orElseThrow(() -> new ResourceNotFoundException("Moneda no encontrada"));

        CondicionPago condicionPago = condicionPagoRepository.findById(request.getIdCondicionPago())
                .orElseThrow(() -> new ResourceNotFoundException("Condición de pago no encontrada"));

        SectorEconomico sector = request.getIdSectorEconomico() != null
                ? sectorEconomicoRepository.findById(request.getIdSectorEconomico())
                        .orElseThrow(() -> new ResourceNotFoundException("Sector económico no encontrado"))
                : cliente.getSectorEconomico();

        EstadoCotizacion estadoBorrador = estadoCotizacionRepository.findByCodigoEstado("BORRADOR")
                .orElseThrow(() -> new BusinessException("No existe el estado BORRADOR configurado"));

        Usuario vendedor = usuarioActual();

        Cotizacion cotizacion = Cotizacion.builder()
                .cliente(cliente)
                .contacto(contacto)
                .usuarioVendedor(vendedor)
                .estadoCotizacion(estadoBorrador)
                .moneda(moneda)
                .condicionPago(condicionPago)
                .sectorEconomico(sector)
                .fechaCotizacion(LocalDate.now())
                .fechaValidez(LocalDate.now().plusDays(15))
                .observaciones(request.getObservaciones())
                .build();
        cotizacion.setUserCreate(vendedor.getNombreUsuario());
        cotizacion.setProcessCreate("ALTA_COTIZACION");
        // codigoCotizacion se deja null: lo genera el trigger de la BD

        cotizacion = cotizacionRepository.save(cotizacion);

        for (CotizacionDetalleRequest lineaReq : request.getLineas()) {
            CotizacionDetalle linea = construirLinea(cotizacion, lineaReq, vendedor.getNombreUsuario());
            cotizacionDetalleRepository.save(linea);
        }
        // La tabla "cotizacion" no guarda el total (se calcula en
        // vw_cotizacion_totales); se hace flush para que la vista ya vea
        // las líneas recién insertadas al armar la respuesta.
        cotizacionDetalleRepository.flush();

        registrarSeguimiento(cotizacion, estadoBorrador, vendedor, "Cotización creada");

        return toResponse(cotizacion);
    }

    @Transactional
    public CotizacionResponse cambiarEstado(Long idCotizacion, CambiarEstadoCotizacionRequest request) {
        Cotizacion cotizacion = obtenerOLanzar(idCotizacion);

        EstadoCotizacion nuevoEstado = estadoCotizacionRepository.findByCodigoEstado(request.getCodigoEstado())
                .orElseThrow(() -> new ResourceNotFoundException("Estado no encontrado: " + request.getCodigoEstado()));

        if (Boolean.TRUE.equals(cotizacion.getEstadoCotizacion().getEsFinal())) {
            throw new BusinessException("No se puede cambiar el estado de una cotización ya finalizada");
        }

        cotizacion.setEstadoCotizacion(nuevoEstado);
        Usuario usuario = usuarioActual();
        cotizacion.setUserUpdate(usuario.getNombreUsuario());
        cotizacion.setProcessUpdate("CAMBIO_ESTADO_COTIZACION");
        cotizacion.setDateUpdate(LocalDateTime.now());
        cotizacion = cotizacionRepository.save(cotizacion);

        registrarSeguimiento(cotizacion, nuevoEstado, usuario, request.getComentario());

        return toResponse(cotizacion);
    }

    @Transactional
    public AdjuntoCotizacionResponse agregarAdjunto(Long idCotizacion, MultipartFile archivo) {
        Cotizacion cotizacion = obtenerOLanzar(idCotizacion);

        String url = cloudinaryService.subirDocumento(archivo, "genlogs/cotizaciones/adjuntos");

        AdjuntoCotizacion adjunto = AdjuntoCotizacion.builder()
                .cotizacion(cotizacion)
                .nombreArchivo(archivo.getOriginalFilename())
                .urlArchivo(url)
                .build();
        adjunto.setUserCreate(usuarioActual().getNombreUsuario());
        adjunto.setProcessCreate("ALTA_ADJUNTO_COTIZACION");

        adjunto = adjuntoCotizacionRepository.save(adjunto);

        return AdjuntoCotizacionResponse.builder()
                .idAdjunto(adjunto.getIdAdjunto())
                .nombreArchivo(adjunto.getNombreArchivo())
                .urlArchivo(adjunto.getUrlArchivo())
                .build();
    }

    @Transactional
    public void eliminarAdjunto(Long idAdjunto) {
        AdjuntoCotizacion adjunto = adjuntoCotizacionRepository.findById(idAdjunto)
                .orElseThrow(() -> new ResourceNotFoundException("Adjunto no encontrado"));

        cloudinaryService.eliminarPorUrl(adjunto.getUrlArchivo(), CloudinaryService.TIPO_DOCUMENTO);

        adjunto.setStatus("I");
        adjunto.setUserUpdate(usuarioActual().getNombreUsuario());
        adjunto.setProcessUpdate("BAJA_ADJUNTO_COTIZACION");
        adjunto.setDateUpdate(LocalDateTime.now());
        adjuntoCotizacionRepository.save(adjunto);
    }

    // ------------------------------------------------------------------

    private CotizacionDetalle construirLinea(Cotizacion cotizacion, CotizacionDetalleRequest req, String usuario) {
        if ((req.getIdProducto() == null) == (req.getIdServicio() == null)) {
            throw new BusinessException("Cada línea debe tener producto O servicio, no ambos ni ninguno");
        }

        UnidadMedida unidad = unidadMedidaRepository.findById(req.getIdUnidadMedida())
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida no encontrada"));

        CotizacionDetalle.CotizacionDetalleBuilder builder = CotizacionDetalle.builder()
                .cotizacion(cotizacion)
                .unidadMedida(unidad)
                .descripcionPersonalizada(req.getDescripcionPersonalizada())
                .cantidad(req.getCantidad())
                .precioUnitario(req.getPrecioUnitario())
                .descuentoUnitario(req.getDescuentoUnitario() != null ? req.getDescuentoUnitario() : BigDecimal.ZERO);

        if (req.getIdProducto() != null) {
            builder.producto(productoRepository.findById(req.getIdProducto())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado")));
        } else {
            builder.servicio(servicioRepository.findById(req.getIdServicio())
                    .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado")));
        }

        CotizacionDetalle linea = builder.build();
        linea.setUserCreate(usuario);
        linea.setProcessCreate("ALTA_LINEA_COTIZACION");
        return linea;
    }

    private void registrarSeguimiento(Cotizacion cotizacion, EstadoCotizacion estado, Usuario usuario, String comentario) {
        SeguimientoCotizacion seguimiento = SeguimientoCotizacion.builder()
                .cotizacion(cotizacion)
                .estadoCotizacion(estado)
                .usuario(usuario)
                .fechaEvento(LocalDateTime.now())
                .comentario(comentario)
                .build();
        seguimiento.setUserCreate(usuario.getNombreUsuario());
        seguimiento.setProcessCreate("SEGUIMIENTO_COTIZACION");
        seguimientoCotizacionRepository.save(seguimiento);
    }

    private Cotizacion obtenerOLanzar(Long idCotizacion) {
        return cotizacionRepository.findById(idCotizacion)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));
    }

    private Usuario usuarioActual() {
        String nombreUsuario = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));
    }

    private CotizacionResponse toResponse(Cotizacion c) {
        TotalesCotizacionProjection totales = cotizacionRepository
                .findTotalesByCotizacion(c.getIdCotizacion())
                .orElse(null);

        return CotizacionResponse.builder()
                .idCotizacion(c.getIdCotizacion())
                .codigoCotizacion(c.getCodigoCotizacion())
                .clienteRazonSocial(c.getCliente().getTercero().getRazonSocial())
                .fechaCotizacion(c.getFechaCotizacion())
                .fechaValidez(c.getFechaValidez())
                .estado(c.getEstadoCotizacion().getNombreEstado())
                .subtotal(totales != null ? totales.getSubtotal() : BigDecimal.ZERO)
                .igv(totales != null ? totales.getIgv() : BigDecimal.ZERO)
                .total(totales != null ? totales.getTotal() : BigDecimal.ZERO)
                .moneda(c.getMoneda().getSimbolo())
                .observaciones(c.getObservaciones())
                .build();
    }

    private CotizacionDetalleResponse toLineaResponse(CotizacionDetalle d) {
        return CotizacionDetalleResponse.builder()
                .idCotizacionDetalle(d.getIdCotizacionDetalle())
                .idProducto(d.getProducto() != null ? d.getProducto().getIdProducto() : null)
                .productoNombre(d.getProducto() != null ? d.getProducto().getNombreProducto() : null)
                .idServicio(d.getServicio() != null ? d.getServicio().getIdServicio() : null)
                .servicioNombre(d.getServicio() != null ? d.getServicio().getNombreServicio() : null)
                .unidadMedida(d.getUnidadMedida().getNombreUnidad())
                .descripcionPersonalizada(d.getDescripcionPersonalizada())
                .cantidad(d.getCantidad())
                .precioUnitario(d.getPrecioUnitario())
                .descuentoUnitario(d.getDescuentoUnitario())
                .importeLinea(d.getImporteLinea())
                .build();
    }
}
