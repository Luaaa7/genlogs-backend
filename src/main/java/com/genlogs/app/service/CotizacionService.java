package com.genlogs.app.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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
import com.genlogs.app.dto.EnviarCotizacionRequest;
import com.genlogs.app.dto.EnviarCotizacionResponse;
import com.genlogs.app.dto.EstadisticasCotizacionResponse;
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
import com.genlogs.app.repository.CotizacionRepository.EstadoCantidadProjection;
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
    private final MailService mailService;
    private final EntityManager entityManager;

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
                        .codigoEstado(s.getEstadoCotizacion().getCodigoEstado())
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
    public void eliminarAdjunto(Long idCotizacion, Long idAdjunto) {
        AdjuntoCotizacion adjunto = adjuntoCotizacionRepository.findById(idAdjunto)
                .orElseThrow(() -> new ResourceNotFoundException("Adjunto no encontrado"));

        if (!adjunto.getCotizacion().getIdCotizacion().equals(idCotizacion)) {
            throw new BusinessException("El adjunto no pertenece a la cotización indicada");
        }

        cloudinaryService.eliminarPorUrl(adjunto.getUrlArchivo(), CloudinaryService.TIPO_DOCUMENTO);

        adjunto.setStatus("I");
        adjunto.setUserUpdate(usuarioActual().getNombreUsuario());
        adjunto.setProcessUpdate("BAJA_ADJUNTO_COTIZACION");
        adjunto.setDateUpdate(LocalDateTime.now());
        adjuntoCotizacionRepository.save(adjunto);
    }

    @Transactional
    public void eliminarAdjunto(Long idAdjunto) {
        AdjuntoCotizacion adjunto = adjuntoCotizacionRepository.findById(idAdjunto)
                .orElseThrow(() -> new ResourceNotFoundException("Adjunto no encontrado"));
        eliminarAdjunto(adjunto.getCotizacion().getIdCotizacion(), idAdjunto);
    }

    /**
     * POST /{id}/enviar — antes 404 garantizado: el frontend llama a este
     * endpoint desde CotizacionDetallePage pero el backend no tenía nada
     * mapeado ahí. Envía la cotización por correo (vía Brevo, igual que
     * MailService.enviarRecuperacion) y, si estaba en BORRADOR, la pasa a
     * ENVIADA registrando el seguimiento correspondiente.
     */
    public EnviarCotizacionResponse enviar(Long idCotizacion, EnviarCotizacionRequest request) {
        // Valida correo obligatorio antes de procesar envío
        if (request == null || request.getCorreoDestinatario() == null || request.getCorreoDestinatario().isBlank()) {
            return EnviarCotizacionResponse.builder()
                    .success(false)
                    .mensaje("El correo destinatario es obligatorio.")
                    .build();
        }

        Cotizacion cotizacion = obtenerOLanzar(idCotizacion);
        Usuario usuario = usuarioActual();

        String nombreCliente = obtenerNombreCliente(cotizacion);
        String cuerpoTexto = construirCuerpoCorreo(cotizacion, request, false);
        String cuerpoHtml = construirCuerpoCorreo(cotizacion, request, true);

        String destinatarioLimpio = request.getCorreoDestinatario().trim();

        // Envío HTTP de correo fuera de la transacción de base de datos para no retener conexiones de HikariCP
        boolean enviado = mailService.enviarCorreo(
                destinatarioLimpio, nombreCliente, request.getAsunto(), cuerpoHtml, cuerpoTexto);

        if (enviado) {
            marcarComoEnviadaSiBorrador(idCotizacion, usuario, destinatarioLimpio);
        }

        return EnviarCotizacionResponse.builder()
                .success(enviado)
                .mensaje(enviado
                        ? "Cotización enviada a " + destinatarioLimpio
                        : "No se pudo enviar el correo. Intenta de nuevo o revisa la configuración de correo.")
                .build();
    }

    @Transactional
    public void marcarComoEnviadaSiBorrador(Long idCotizacion, Usuario usuario, String destinatario) {
        Cotizacion cotizacion = obtenerOLanzar(idCotizacion);
        if ("BORRADOR".equals(cotizacion.getEstadoCotizacion().getCodigoEstado())) {
            EstadoCotizacion estadoEnviada = estadoCotizacionRepository.findByCodigoEstado("ENVIADA")
                    .orElse(null);
            if (estadoEnviada != null) {
                cotizacion.setEstadoCotizacion(estadoEnviada);
                cotizacion.setUserUpdate(usuario.getNombreUsuario());
                cotizacion.setProcessUpdate("ENVIO_COTIZACION");
                cotizacion.setDateUpdate(LocalDateTime.now());
                cotizacionRepository.save(cotizacion);
                registrarSeguimiento(cotizacion, estadoEnviada,
                        usuario, "Cotización enviada a " + destinatario);
            }
        }
    }

    /**
     * GET /{id}/descargar-pdf — antes 404 garantizado (no existía el mapeo).
     * Genera un PDF de una sola cotización (cliente, código, fechas, líneas
     * y totales) reutilizando el patrón de ReporteService (PDFBox puro).
     */
    @Transactional(readOnly = true)
    public byte[] descargarPdf(Long idCotizacion) {
        Cotizacion cotizacion = obtenerOLanzar(idCotizacion);
        List<CotizacionDetalle> lineas = cotizacionDetalleRepository.findByCotizacion(idCotizacion);
        TotalesCotizacionProjection totales = cotizacionRepository.findTotalesByCotizacion(idCotizacion).orElse(null);
        return generarPdfCotizacion(cotizacion, lineas, totales);
    }

    /**
     * POST /{id}/duplicar — antes 404 garantizado. Clona cabecera (cliente,
     * moneda, condición de pago, sector, observaciones) y líneas de una
     * cotización existente en una nueva cotización BORRADOR; el código lo
     * vuelve a generar el trigger de la BD (se deja null, igual que en crear()).
     */
    @Transactional
    public CotizacionResponse duplicar(Long idCotizacion) {
        Cotizacion original = obtenerOLanzar(idCotizacion);

        EstadoCotizacion estadoBorrador = estadoCotizacionRepository.findByCodigoEstado("BORRADOR")
                .orElseThrow(() -> new BusinessException("No existe el estado BORRADOR configurado"));

        Usuario usuario = usuarioActual();

        Cotizacion copia = Cotizacion.builder()
                .cliente(original.getCliente())
                .contacto(original.getContacto())
                .usuarioVendedor(usuario)
                .estadoCotizacion(estadoBorrador)
                .moneda(original.getMoneda())
                .condicionPago(original.getCondicionPago())
                .sectorEconomico(original.getSectorEconomico())
                .fechaCotizacion(LocalDate.now())
                .fechaValidez(LocalDate.now().plusDays(15))
                .observaciones(original.getObservaciones())
                .build();
        copia.setUserCreate(usuario.getNombreUsuario());
        copia.setProcessCreate("DUPLICAR_COTIZACION");

        copia = cotizacionRepository.save(copia);

        for (CotizacionDetalle lineaOriginal : cotizacionDetalleRepository.findByCotizacion(idCotizacion)) {
            CotizacionDetalle.CotizacionDetalleBuilder builder = CotizacionDetalle.builder()
                    .cotizacion(copia)
                    .producto(lineaOriginal.getProducto())
                    .servicio(lineaOriginal.getServicio())
                    .unidadMedida(lineaOriginal.getUnidadMedida())
                    .descripcionPersonalizada(lineaOriginal.getDescripcionPersonalizada())
                    .cantidad(lineaOriginal.getCantidad())
                    .precioUnitario(lineaOriginal.getPrecioUnitario())
                    .descuentoUnitario(lineaOriginal.getDescuentoUnitario());
            CotizacionDetalle lineaCopia = builder.build();
            lineaCopia.setUserCreate(usuario.getNombreUsuario());
            lineaCopia.setProcessCreate("DUPLICAR_LINEA_COTIZACION");
            cotizacionDetalleRepository.save(lineaCopia);
        }
        cotizacionDetalleRepository.flush();

        registrarSeguimiento(copia, estadoBorrador, usuario,
                "Duplicada desde " + original.getCodigoCotizacion());

        return toResponse(copia);
    }

    /**
     * GET /estadisticas — antes 404 garantizado. Reutiliza
     * countAgrupadoPorEstado() (ya usado por el Dashboard) y agrega el monto
     * pendiente (vw_cotizacion_totales de las no finales) y la tasa de
     * aprobación, que es lo que espera cotizacionesApi.obtenerEstadisticas().
     */
    @Transactional(readOnly = true)
    public EstadisticasCotizacionResponse obtenerEstadisticas() {
        List<EstadoCantidadProjection> agrupado = cotizacionRepository.countAgrupadoPorEstado();

        Map<String, Long> totalPorEstado = new LinkedHashMap<>();
        long total = 0;
        for (EstadoCantidadProjection p : agrupado) {
            totalPorEstado.put(p.getEstado(), p.getCantidad());
            total += p.getCantidad();
        }

        long aprobadas = cotizacionRepository.countAprobadas();
        BigDecimal montoPendiente = cotizacionRepository.sumTotalPendiente();
        double tasaAprobacion = total > 0
                ? BigDecimal.valueOf(aprobadas * 100.0 / total).setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        return EstadisticasCotizacionResponse.builder()
                .totalCotizaciones(total)
                .totalPorEstado(totalPorEstado)
                .montoTotalPendiente(montoPendiente != null ? montoPendiente : BigDecimal.ZERO)
                .tasaAprobacion(tasaAprobacion)
                .build();
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

    private static final List<String> COMENTARIOS_TRIGGER = List.of("Cotización registrada", "Cambio de estado");

    /**
     * La BD ya registra el seguimiento sola (trigger fn_cotizacion_seguimiento)
     * al crear la cotizacion o cambiarle el estado. Para no duplicar el
     * historial, aqui solo se reemplaza el comentario generico de esa fila por
     * el comentario real; si el trigger no dejo fila, se inserta una como antes.
     */
    private void registrarSeguimiento(Cotizacion cotizacion, EstadoCotizacion estado, Usuario usuario, String comentario) {
        cotizacionRepository.flush();
        SeguimientoCotizacion delTrigger = seguimientoCotizacionRepository
                .findByCotizacion(cotizacion.getIdCotizacion()).stream()
                .filter(s -> s.getEstadoCotizacion().getIdEstadoCotizacion().equals(estado.getIdEstadoCotizacion()))
                .filter(s -> COMENTARIOS_TRIGGER.contains(s.getComentario()))
                .max((x, y) -> Long.compare(x.getIdSeguimiento(), y.getIdSeguimiento()))
                .orElse(null);
        if (delTrigger != null) {
            if (comentario != null && !comentario.isBlank()) {
                delTrigger.setComentario(comentario);
                seguimientoCotizacionRepository.save(delTrigger);
            }
            return;
        }
        insertarSeguimiento(cotizacion, estado, usuario, comentario);
    }

    private void insertarSeguimiento(Cotizacion cotizacion, EstadoCotizacion estado, Usuario usuario, String comentario) {
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
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));
        fijarAuditoriaBD(usuario.getNombreUsuario());
        return usuario;
    }

    /**
     * Los triggers de la BD (p. ej. fn_cotizacion_seguimiento, que inserta el
     * historial al crear/cambiar de estado) rellenan user_create/process_create
     * desde app.user/app.process. Si no se fijan, esas filas llegan con NULL y
     * el NOT NULL las rechaza (409 al crear/duplicar). El tercer argumento
     * "true" limita el valor a la transaccion actual (seguro con el pooler).
     */
    private void fijarAuditoriaBD(String nombreUsuario) {
        entityManager.createNativeQuery(
                        "select set_config('app.user', :u, true), set_config('app.process', :p, true)")
                .setParameter("u", nombreUsuario)
                .setParameter("p", "FRM_COTIZACION")
                .getSingleResult();
    }

    private CotizacionResponse toResponse(Cotizacion c) {
        TotalesCotizacionProjection totales = cotizacionRepository
                .findTotalesByCotizacion(c.getIdCotizacion())
                .orElse(null);

        // Fallback a cero si proyecciones vienen nulas
        BigDecimal subtotal = (totales != null && totales.getSubtotal() != null) ? totales.getSubtotal() : BigDecimal.ZERO;
        BigDecimal igv = (totales != null && totales.getIgv() != null) ? totales.getIgv() : BigDecimal.ZERO;
        BigDecimal total = (totales != null && totales.getTotal() != null) ? totales.getTotal() : BigDecimal.ZERO;

        return CotizacionResponse.builder()
                .idCotizacion(c.getIdCotizacion())
                .codigoCotizacion(c.getCodigoCotizacion())
                .clienteRazonSocial(obtenerNombreCliente(c))
                .fechaCotizacion(c.getFechaCotizacion())
                .fechaValidez(c.getFechaValidez())
                .estado(c.getEstadoCotizacion() != null ? c.getEstadoCotizacion().getNombreEstado() : null)
                .subtotal(subtotal)
                .igv(igv)
                .total(total)
                .moneda(c.getMoneda() != null ? c.getMoneda().getSimbolo() : "S/")
                .monedaCodigo(c.getMoneda() != null ? c.getMoneda().getCodigoMoneda() : null)
                .condicionPago(c.getCondicionPago() != null ? c.getCondicionPago().getNombreCondicion() : null)
                .idCliente(c.getCliente() != null ? c.getCliente().getIdCliente() : null)
                .clienteEmail(obtenerCorreoCliente(c))
                .observaciones(c.getObservaciones())
                .build();
    }

    private String construirCuerpoCorreo(Cotizacion c, EnviarCotizacionRequest request, boolean html) {
        TotalesCotizacionProjection totales = cotizacionRepository.findTotalesByCotizacion(c.getIdCotizacion()).orElse(null);
        BigDecimal total = (totales != null && totales.getTotal() != null) ? totales.getTotal() : BigDecimal.ZERO;
        String totalStr = total.toPlainString();
        String simbolo = c.getMoneda() != null ? c.getMoneda().getSimbolo() : "S/";

        StringBuilder sb = new StringBuilder();
        if (request.getMensaje() != null && !request.getMensaje().isBlank()) {
            sb.append(request.getMensaje()).append(html ? "<br><br>" : "\n\n");
        }
        sb.append("Cotización ").append(c.getCodigoCotizacion())
                .append(" — ").append(obtenerNombreCliente(c));
        if (html) {
            sb.append("<br>");
        } else {
            sb.append("\n");
        }
        String fechaCot = c.getFechaCotizacion() != null ? c.getFechaCotizacion().format(FMT_FECHA) : "-";
        String fechaVal = c.getFechaValidez() != null ? c.getFechaValidez().format(FMT_FECHA) : "-";
        sb.append("Fecha: ").append(fechaCot)
                .append(" · Válida hasta: ").append(fechaVal);

        if (request.isIncluirDetalles()) {
            sb.append(html ? "<br>" : "\n").append("Total: ").append(simbolo).append(" ").append(totalStr);
        }
        return sb.toString();
    }

    /**
     * PDF de una sola cotización: cabecera (cliente/código/fechas), tabla de
     * líneas y totales. Mismo patrón PDFBox que ReporteService.generarPdf,
     * adaptado para un único documento en vez de un listado tabular plano.
     */
    private byte[] generarPdfCotizacion(Cotizacion c, List<CotizacionDetalle> lineas, TotalesCotizacionProjection totales) {
        String simbolo = c.getMoneda() != null ? c.getMoneda().getSimbolo() : "S/";
        String[] encabezados = { "Descripción", "Cant.", "P. Unit.", "Importe" };
        String[][] datos = new String[lineas.size()][];
        for (int i = 0; i < lineas.size(); i++) {
            CotizacionDetalle d = lineas.get(i);
            String descripcion = d.getProducto() != null ? d.getProducto().getNombreProducto()
                    : d.getServicio() != null ? d.getServicio().getNombreServicio()
                    : d.getDescripcionPersonalizada() != null ? d.getDescripcionPersonalizada() : "-";
            // Calcula importe si línea aún no tiene generado
            BigDecimal cant = d.getCantidad() != null ? d.getCantidad() : BigDecimal.ZERO;
            BigDecimal precio = d.getPrecioUnitario() != null ? d.getPrecioUnitario() : BigDecimal.ZERO;
            BigDecimal desc = d.getDescuentoUnitario() != null ? d.getDescuentoUnitario() : BigDecimal.ZERO;
            BigDecimal importe = d.getImporteLinea() != null
                    ? d.getImporteLinea()
                    : cant.multiply(precio.subtract(desc)).setScale(2, RoundingMode.HALF_UP);

            datos[i] = new String[] {
                    descripcion,
                    cant.toPlainString(),
                    precio.toPlainString(),
                    importe.toPlainString(),
            };
        }

        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font fuenteTitulo = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fuenteTexto = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float margenIzq = 40;
            float anchoUtil = PDRectangle.A4.getWidth() - 2 * margenIzq;
            // Anchos proporcionales para evitar solapamiento de columnas
            float[] anchosColumnas = {
                    anchoUtil * 0.52f,
                    anchoUtil * 0.14f,
                    anchoUtil * 0.17f,
                    anchoUtil * 0.17f
            };
            float alturaLinea = 16;
            float yInicio = PDRectangle.A4.getHeight() - 50;

            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            PDPageContentStream cs = new PDPageContentStream(documento, pagina);
            float y = yInicio;

            String codigo = c.getCodigoCotizacion() != null ? c.getCodigoCotizacion() : "Borrador";

            cs.setFont(fuenteTitulo, 14);
            cs.beginText();
            cs.newLineAtOffset(margenIzq, y);
            cs.showText(ajustarTexto("Cotización " + codigo, fuenteTitulo, 14, anchoUtil));
            cs.endText();
            y -= alturaLinea * 1.4f;

            String cliente = obtenerNombreCliente(c);
            cs.setFont(fuenteTexto, 10);
            cs.beginText();
            cs.newLineAtOffset(margenIzq, y);
            cs.showText(ajustarTexto("Cliente: " + cliente, fuenteTexto, 10, anchoUtil));
            cs.endText();
            y -= alturaLinea;

            String fechaCotStr = c.getFechaCotizacion() != null ? c.getFechaCotizacion().format(FMT_FECHA) : "-";
            String fechaValStr = c.getFechaValidez() != null ? c.getFechaValidez().format(FMT_FECHA) : "-";

            cs.beginText();
            cs.newLineAtOffset(margenIzq, y);
            cs.showText("Fecha: " + fechaCotStr + "   Válida hasta: " + fechaValStr);
            cs.endText();
            y -= alturaLinea * 1.8f;

            y = escribirFilaPdf(cs, fuenteTitulo, encabezados, margenIzq, y, anchosColumnas, alturaLinea);

            for (String[] fila : datos) {
                if (y < 100) {
                    cs.close();
                    pagina = new PDPage(PDRectangle.A4);
                    documento.addPage(pagina);
                    cs = new PDPageContentStream(documento, pagina);
                    y = yInicio;
                }
                y = escribirFilaPdf(cs, fuenteTexto, fila, margenIzq, y, anchosColumnas, alturaLinea);
            }

            y -= alturaLinea;
            BigDecimal subtotalBd = (totales != null && totales.getSubtotal() != null) ? totales.getSubtotal() : BigDecimal.ZERO;
            BigDecimal igvBd = (totales != null && totales.getIgv() != null) ? totales.getIgv() : BigDecimal.ZERO;
            BigDecimal totalBd = (totales != null && totales.getTotal() != null) ? totales.getTotal() : BigDecimal.ZERO;

            cs.setFont(fuenteTexto, 10);
            for (String linea : List.of(
                    "Subtotal: " + simbolo + " " + subtotalBd.toPlainString(),
                    "IGV (18%): " + simbolo + " " + igvBd.toPlainString())) {
                if (y < 50) {
                    cs.close();
                    pagina = new PDPage(PDRectangle.A4);
                    documento.addPage(pagina);
                    cs = new PDPageContentStream(documento, pagina);
                    y = yInicio;
                }
                cs.beginText();
                cs.newLineAtOffset(PDRectangle.A4.getWidth() - margenIzq - 220, y);
                cs.showText(linea);
                cs.endText();
                y -= alturaLinea;
            }
            cs.setFont(fuenteTitulo, 11);
            cs.beginText();
            cs.newLineAtOffset(PDRectangle.A4.getWidth() - margenIzq - 220, y);
            cs.showText("Total: " + simbolo + " " + totalBd.toPlainString());
            cs.endText();

            cs.close();
            documento.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de la cotización", e);
        }
    }

    private float escribirFilaPdf(PDPageContentStream cs, PDType1Font fuente, String[] valores,
            float margenIzq, float y, float[] anchosColumnas, float alturaLinea) throws IOException {
        cs.setFont(fuente, 9);
        float x = margenIzq;
        for (int col = 0; col < valores.length; col++) {
            float anchoCol = anchosColumnas[col];
            String texto = ajustarTexto(valores[col], fuente, 9, anchoCol - 4);
            cs.beginText();
            cs.newLineAtOffset(x, y);
            cs.showText(texto);
            cs.endText();
            x += anchoCol;
        }
        return y - alturaLinea;
    }

    // Sanea caracteres y saltos de línea para PDFBox
    private static String sanitizarTextoPdf(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replace('\u2014', '-')
                .replace('\u2013', '-')
                .replace('\u2022', '*')
                .replace('\u201c', '"')
                .replace('\u201d', '"')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", " ")
                .trim();
    }

    // Trunca texto largo con elipsis evitando desbordes
    private static String ajustarTexto(String texto, PDType1Font fuente, float fontSize, float anchoMaximo) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String limpio = sanitizarTextoPdf(texto);
        try {
            float ancho = (fuente.getStringWidth(limpio) / 1000f) * fontSize;
            if (ancho <= anchoMaximo) {
                return limpio;
            }
            while (limpio.length() > 3 && (fuente.getStringWidth(limpio + "...") / 1000f) * fontSize > anchoMaximo) {
                limpio = limpio.substring(0, limpio.length() - 1);
            }
            return limpio + "...";
        } catch (Exception e) {
            return limpio.length() > 35 ? limpio.substring(0, 32) + "..." : limpio;
        }
    }

    // Obtiene razón social o documento con fallback seguro
    private String obtenerCorreoCliente(Cotizacion c) {
        if (c.getContacto() != null && c.getContacto().getCorreo() != null && !c.getContacto().getCorreo().isBlank()) {
            return c.getContacto().getCorreo();
        }
        if (c.getCliente() != null && c.getCliente().getTercero() != null) {
            return c.getCliente().getTercero().getCorreo();
        }
        return null;
    }

    private String obtenerNombreCliente(Cotizacion c) {
        try {
            if (c.getCliente() != null && c.getCliente().getTercero() != null) {
                String rs = c.getCliente().getTercero().getRazonSocial();
                if (rs != null && !rs.isBlank()) {
                    return rs;
                }
                String doc = c.getCliente().getTercero().getNumeroDocumento();
                if (doc != null && !doc.isBlank()) {
                    return doc;
                }
            }
        } catch (Exception ignored) {
        }
        return "Cliente";
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
