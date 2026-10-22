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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.genlogs.app.service.pdf.PdfGenerationService;
import com.genlogs.app.service.pdf.PdfGenerationService.CotizacionPdfData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import com.genlogs.app.util.PdfUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.SectorEconomico;
import com.genlogs.app.model.SeguimientoCotizacion;
import com.genlogs.app.model.Servicio;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.AdjuntoCotizacionRepository;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.CondicionPagoRepository;
import com.genlogs.app.repository.ContactoTerceroRepository;
import com.genlogs.app.repository.CotizacionDetalleRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.CotizacionRepository.EstadoCantidadProjection;
import com.genlogs.app.repository.CotizacionRepository.TotalesCotizacionPorIdProjection;
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
    private final PdfGenerationService pdfGenerationService;

    private static final Logger log = LoggerFactory.getLogger(CotizacionService.class);
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
    /**
     * Transacción explícita para los puntos del flujo que NO pueden usar
     * @Transactional (self-invocation) o que deben cerrar la transacción antes
     * de una llamada HTTP saliente (ver enviar()).
     */
    private final TransactionTemplate transactionTemplate;

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Transactional(readOnly = true)
    public CotizacionResponse buscarPorId(Long idCotizacion) {
        return toResponseDetalle(obtenerOLanzar(idCotizacion));
    }

    @Transactional(readOnly = true)
    public CotizacionResponse buscarPorCodigo(String codigoCotizacion) {
        Cotizacion c = cotizacionRepository.findByCodigoCotizacion(codigoCotizacion)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));
        return toResponseDetalle(c);
    }

    /**
     * Listado paginado con filtros opcionales, para CotizacionesListPage.tsx.
     *
     * Los totales de todas las filas de la página se piden en UNA sola consulta
     * a vw_cotizacion_totales (antes era una consulta por fila).
     */
    @Transactional(readOnly = true)
    public PaginaResponse<CotizacionResponse> listar(String codigoEstado, Long idCliente, String codigoMoneda, Pageable pageable) {
        Page<Cotizacion> pagina = cotizacionRepository.buscarPaginado(codigoEstado, idCliente, codigoMoneda, pageable);
        Map<Long, TotalesCotizacionProjection> totales = cargarTotales(pagina.getContent());
        return PaginaResponse.of(pagina.map(c -> toResponse(c, totales)));
    }

    @Transactional(readOnly = true)
    public List<CotizacionDetalleResponse> listarLineas(Long idCotizacion) {
        return cotizacionDetalleRepository.findByCotizacion(idCotizacion).stream()
                .map(this::toLineaResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SeguimientoCotizacionResponse> listarSeguimiento(Long idCotizacion) {
        return mapearSeguimientos(seguimientoCotizacionRepository.findByCotizacion(idCotizacion));
    }

    @Transactional(readOnly = true)
    public List<AdjuntoCotizacionResponse> listarAdjuntos(Long idCotizacion) {
        return adjuntoCotizacionRepository.findByCotizacion(idCotizacion).stream()
                .map(this::toAdjuntoResponse)
                .toList();
    }

    @Transactional
    public CotizacionResponse crear(CotizacionRequest request) {
        Long idCliente = resolverIdCliente(request);
        Long idContacto = request.getIdContacto();
        Integer idMoneda = resolverIdMoneda(request);
        Integer idCondicionPago = resolverIdCondicionPago(request);
        List<CotizacionDetalleRequest> lineas = resolverLineas(request);

        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));

        ContactoTercero contacto = null;
        if (idContacto != null) {
            contacto = contactoTerceroRepository.findById(idContacto)
                    .orElseThrow(() -> new ResourceNotFoundException("Contacto no encontrado"));

            if (!contacto.getTercero().getIdTercero().equals(cliente.getTercero().getIdTercero())) {
                throw new BusinessException("El contacto no pertenece a este cliente");
            }
        }

        Moneda moneda = monedaRepository.findById(idMoneda)
                .orElseThrow(() -> new ResourceNotFoundException("Moneda no encontrada"));

        CondicionPago condicionPago = condicionPagoRepository.findById(idCondicionPago)
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

        for (CotizacionDetalleRequest lineaReq : lineas) {
            CotizacionDetalle linea = construirLinea(cotizacion, lineaReq, vendedor.getNombreUsuario());
            cotizacionDetalleRepository.save(linea);
        }
        // La tabla "cotizacion" no guarda el total (se calcula en
        // vw_cotizacion_totales); se hace flush para que la vista ya vea
        // las líneas recién insertadas al armar la respuesta.
        cotizacionDetalleRepository.flush();

        // El código lo asigna un trigger de la BD: hay que releerlo, porque la
        // entidad en memoria sigue con null (si el trigger no existiera, se
        // genera uno determinista para no dejar la cotización sin código).
        String codigoGenerado = resolverCodigoCotizacion(cotizacion);

        registrarSeguimiento(cotizacion, estadoBorrador, vendedor, "Cotización creada");

        return toResponseDetalle(cotizacion, codigoGenerado);
    }

    @Transactional
    public CotizacionResponse cambiarEstado(Long idCotizacion, CambiarEstadoCotizacionRequest request) {
        Cotizacion cotizacion = obtenerOLanzar(idCotizacion);

        String codigoEstado = request.codigoEstadoResuelto();
        if (codigoEstado == null || codigoEstado.isBlank()) {
            throw new BusinessException("Debe indicar el código del nuevo estado");
        }

        EstadoCotizacion nuevoEstado = buscarEstado(codigoEstado);

        if (Boolean.TRUE.equals(cotizacion.getEstadoCotizacion().getEsFinal())) {
            throw new BusinessException("No se puede cambiar el estado de una cotización ya finalizada");
        }

        cotizacion.setEstadoCotizacion(nuevoEstado);
        Usuario usuario = usuarioActual();
        cotizacion.setUserUpdate(usuario.getNombreUsuario());
        cotizacion.setProcessUpdate("CAMBIO_ESTADO_COTIZACION");
        cotizacion.setDateUpdate(LocalDateTime.now());
        cotizacion = cotizacionRepository.save(cotizacion);

        registrarSeguimiento(cotizacion, nuevoEstado, usuario, request.comentarioResuelto());

        return toResponseDetalle(cotizacion);
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
                .id(adjunto.getIdAdjunto())
                .nombreArchivo(adjunto.getNombreArchivo())
                .urlArchivo(adjunto.getUrlArchivo())
                .urlCloudinary(adjunto.getUrlArchivo())
                .tipoArchivo(extensionDe(adjunto.getNombreArchivo()))
                // El tamaño se conoce en la subida: la tabla no lo guarda, así
                // que se devuelve ahora y el listado lo reporta como 0 (el
                // frontend formatea el valor sin validarlo y no admite null).
                .tamanio(archivo.getSize())
                .fechaSubida(adjunto.getDateCreate())
                .subidoPor(adjunto.getUserCreate())
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

        // El contenido del correo se arma dentro de una transacción explícita:
        // cliente→tercero y moneda son asociaciones LAZY y, sin sesión
        // abierta, saltaría LazyInitializationException (el flujo ya no
        // depende de spring.jpa.open-in-view).
        DatosEnvio datos = transactionTemplate.execute(status -> {
            Cotizacion cotizacion = obtenerOLanzar(idCotizacion);
            return new DatosEnvio(
                    obtenerNombreCliente(cotizacion),
                    construirCuerpoCorreo(cotizacion, request, false),
                    construirCuerpoCorreo(cotizacion, request, true),
                    usuarioActual());
        });

        if (datos == null) {
            return EnviarCotizacionResponse.builder()
                    .success(false)
                    .mensaje("No se pudo procesar la cotización. Intenta de nuevo.")
                    .build();
        }

        String destinatarioLimpio = request.getCorreoDestinatario().trim();

        // Envío HTTP de correo fuera de la transacción de base de datos para no retener conexiones de HikariCP
        boolean enviado = mailService.enviarCorreo(
                destinatarioLimpio, datos.nombreCliente(), request.getAsunto(), datos.cuerpoHtml(), datos.cuerpoTexto());

        if (enviado) {
            // El cambio de estado se hace en su propia transacción: antes se
            // llamaba a un método @Transactional desde la misma clase
            // (auto-invocación) y la anotación nunca se aplicaba.
            Usuario usuario = datos.usuario();
            transactionTemplate.executeWithoutResult(status ->
                    marcarComoEnviadaSiBorrador(idCotizacion, usuario, destinatarioLimpio));
        }

        return EnviarCotizacionResponse.builder()
                .success(enviado)
                .mensaje(enviado
                        ? "Cotización enviada a " + destinatarioLimpio
                        : "No se pudo enviar el correo. Intenta de nuevo o revisa la configuración de correo.")
                .build();
    }

    /**
     * Cambia BORRADOR → ENVIADA. La transacción la abre quien la invoca
     * (TransactionTemplate): si se anotara con @Transactional y se llamara
     * desde esta misma clase, el proxy no se aplicaría y la anotación sería
     * inoperante.
     */
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
     * y totales) delegando a {@link PdfGenerationService}.
     */
    @Transactional(readOnly = true)
    public byte[] descargarPdf(Long idCotizacion) {
        Cotizacion cotizacion = obtenerOLanzar(idCotizacion);
        List<CotizacionDetalle> lineas = cotizacionDetalleRepository.findByCotizacion(idCotizacion);
        TotalesCotizacionProjection totales = cotizacionRepository.findTotalesByCotizacion(idCotizacion).orElse(null);

        String simbolo = cotizacion.getMoneda() != null ? cotizacion.getMoneda().getSimbolo() : "S/";
        List<String[]> filas = new ArrayList<>();
        for (CotizacionDetalle d : lineas) {
            String descripcion = d.getProducto() != null ? d.getProducto().getNombreProducto()
                    : d.getServicio() != null ? d.getServicio().getNombreServicio()
                    : d.getDescripcionPersonalizada() != null ? d.getDescripcionPersonalizada() : "-";
            BigDecimal cant = d.getCantidad() != null ? d.getCantidad() : BigDecimal.ZERO;
            BigDecimal precio = d.getPrecioUnitario() != null ? d.getPrecioUnitario() : BigDecimal.ZERO;
            BigDecimal desc = d.getDescuentoUnitario() != null ? d.getDescuentoUnitario() : BigDecimal.ZERO;
            BigDecimal importe = d.getImporteLinea() != null ? d.getImporteLinea() : calcularImporteLinea(d);
            filas.add(new String[] { descripcion, cant.toPlainString(), precio.toPlainString(), importe.toPlainString() });
        }

        CotizacionPdfData data = new CotizacionPdfData(
                cotizacion.getCodigoCotizacion() != null ? cotizacion.getCodigoCotizacion() : "Borrador",
                obtenerNombreCliente(cotizacion),
                cotizacion.getFechaCotizacion(),
                cotizacion.getFechaValidez(),
                simbolo,
                filas,
                totales != null && totales.getSubtotal() != null ? totales.getSubtotal() : BigDecimal.ZERO,
                totales != null && totales.getIgv() != null ? totales.getIgv() : BigDecimal.ZERO,
                totales != null && totales.getTotal() != null ? totales.getTotal() : BigDecimal.ZERO
        );

        return pdfGenerationService.generarPdfCotizacion(data);
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

        // El código lo asigna el trigger de la BD: hay que releerlo (ver crear()).
        String codigoGenerado = resolverCodigoCotizacion(copia);

        registrarSeguimiento(copia, estadoBorrador, usuario,
                "Duplicada desde " + original.getCodigoCotizacion());

        return toResponseDetalle(copia, codigoGenerado);
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
        String descripcion = descripcionDeLinea(req);
        boolean sinCatalogo = req.getIdProducto() == null && req.getIdServicio() == null;

        if (!sinCatalogo && (req.getIdProducto() == null) == (req.getIdServicio() == null)) {
            throw new BusinessException("Cada línea debe tener producto O servicio, no ambos ni ninguno");
        }
        if (sinCatalogo && (descripcion == null || descripcion.isBlank())) {
            throw new BusinessException("Cada línea debe tener producto O servicio, o una descripción");
        }

        Producto producto = null;
        Servicio servicio = null;
        if (req.getIdProducto() != null) {
            producto = productoRepository.findById(req.getIdProducto())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
        } else if (req.getIdServicio() != null) {
            servicio = servicioRepository.findById(req.getIdServicio())
                    .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado"));
        }

        UnidadMedida unidad = resolverUnidadMedida(req, producto);

        CotizacionDetalle.CotizacionDetalleBuilder builder = CotizacionDetalle.builder()
                .cotizacion(cotizacion)
                .producto(producto)
                .servicio(servicio)
                .unidadMedida(unidad)
                .descripcionPersonalizada(descripcion)
                .cantidad(req.getCantidad())
                .precioUnitario(req.getPrecioUnitario())
                .descuentoUnitario(req.getDescuentoUnitario() != null ? req.getDescuentoUnitario() : BigDecimal.ZERO);

        CotizacionDetalle linea = builder.build();
        linea.setUserCreate(usuario);
        linea.setProcessCreate("ALTA_LINEA_COTIZACION");
        return linea;
    }

    /**
     * Unidad de medida de la línea: la indicada, la del producto o, si no hay
     * ninguna, la unidad activa por defecto (el frontend no manda
     * idUnidadMedida cuando la línea es solo descriptiva).
     */
    private UnidadMedida resolverUnidadMedida(CotizacionDetalleRequest req, Producto producto) {
        if (req.getIdUnidadMedida() != null) {
            return unidadMedidaRepository.findById(req.getIdUnidadMedida())
                    .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida no encontrada"));
        }
        if (producto != null && producto.getUnidadMedida() != null) {
            return producto.getUnidadMedida();
        }
        return unidadMedidaRepository.findFirstByStatusOrderByIdUnidadMedidaAsc("A")
                .orElseThrow(() -> new ResourceNotFoundException("No hay unidades de medida activas configuradas"));
    }

    /** Descripción libre de la línea: acepta los nombres que usa el frontend. */
    private String descripcionDeLinea(CotizacionDetalleRequest req) {
        if (req.getDescripcionPersonalizada() != null && !req.getDescripcionPersonalizada().isBlank()) {
            return req.getDescripcionPersonalizada();
        }
        if (req.getDescripcion() != null && !req.getDescripcion().isBlank()) {
            return req.getDescripcion();
        }
        if (req.getProducto() != null && !req.getProducto().isBlank()) {
            return req.getProducto();
        }
        if (req.getServicio() != null && !req.getServicio().isBlank()) {
            return req.getServicio();
        }
        return null;
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("No hay un usuario autenticado en la petición");
        }
        return usuarioRepository.findByNombreUsuario(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));
    }

    /**
     * Totales de todas las cotizaciones de la página en UNA sola consulta
     * (indexados por id). Evita el N+1 de consultar la vista por cada fila.
     */
    private Map<Long, TotalesCotizacionProjection> cargarTotales(Collection<Cotizacion> cotizaciones) {
        Map<Long, TotalesCotizacionProjection> totales = new HashMap<>();
        if (cotizaciones == null || cotizaciones.isEmpty()) {
            return totales;
        }
        List<Long> ids = cotizaciones.stream().map(Cotizacion::getIdCotizacion).toList();
        for (TotalesCotizacionPorIdProjection t : cotizacionRepository.findTotalesPorCotizaciones(ids)) {
            totales.put(t.getIdCotizacion(), t);
        }
        return totales;
    }

    /**
     * Devuelve el código de la cotización tal como quedó en la BD (lo asigna
     * un trigger). Si el trigger no existiera, genera uno determinista para no
     * dejar la cotización sin código.
     */
    private String resolverCodigoCotizacion(Cotizacion cotizacion) {
        String codigo = cotizacionRepository.findCodigoCotizacionGenerado(cotizacion.getIdCotizacion())
                .orElse(null);
        if (codigo == null || codigo.isBlank()) {
            codigo = "COT-" + cotizacion.getIdCotizacion();
            cotizacion.setCodigoCotizacion(codigo);
            cotizacionRepository.save(cotizacion);
        }
        return codigo;
    }

    // ---- Resolución de los alias que envía el frontend ----

    private Long resolverIdCliente(CotizacionRequest request) {
        Long id = request.getIdCliente() != null ? request.getIdCliente() : request.getClienteId();
        if (id == null) {
            throw new BusinessException("Debe indicar el cliente");
        }
        return id;
    }

    private Integer resolverIdMoneda(CotizacionRequest request) {
        if (request.getIdMoneda() != null) {
            return request.getIdMoneda();
        }
        String codigo = request.getMoneda();
        if (codigo == null || codigo.isBlank()) {
            throw new BusinessException("Debe indicar la moneda");
        }
        return monedaRepository.findByCodigoMoneda(codigo.trim().toUpperCase())
                .map(Moneda::getIdMoneda)
                .orElseThrow(() -> new ResourceNotFoundException("Moneda no encontrada: " + codigo));
    }

    private Integer resolverIdCondicionPago(CotizacionRequest request) {
        if (request.getIdCondicionPago() != null) {
            return request.getIdCondicionPago();
        }
        String codigo = request.getCondicionPago();
        if (codigo == null || codigo.isBlank()) {
            throw new BusinessException("Debe indicar la condición de pago");
        }

        String normalizado = codigo.trim();
        Optional<CondicionPago> condicion = condicionPagoRepository.findByNombreCondicion(normalizado);
        if (condicion.isEmpty()) {
            condicion = condicionPagoRepository.findFirstByNombreCondicionIgnoreCase(normalizado);
        }
        if (condicion.isEmpty()) {
            // El frontend manda un código (CREDITO_30); si en la BD la
            // condición se guardó con otro texto ("Crédito 30 días") se
            // resuelve por los días de crédito.
            Short dias = diasCreditoDe(normalizado);
            if (dias != null) {
                condicion = condicionPagoRepository
                        .findFirstByDiasCreditoAndStatusOrderByIdCondicionPagoAsc(dias, "A");
            }
        }

        return condicion.map(CondicionPago::getIdCondicionPago)
                .orElseThrow(() -> new ResourceNotFoundException("Condición de pago no encontrada: " + codigo));
    }

    /**
     * Días de crédito que codifica un valor del frontend: "CONTADO" -> 0,
     * "CREDITO_30" -> 30. Devuelve null si el texto no sigue ese patrón.
     */
    private Short diasCreditoDe(String codigo) {
        if (codigo == null) {
            return null;
        }
        String texto = codigo.trim().toUpperCase(Locale.ROOT);
        if ("CONTADO".equals(texto) || "AL_CONTADO".equals(texto)) {
            return Short.valueOf((short) 0);
        }
        Matcher matcher = Pattern.compile("^(?:CREDITO[_\\s-]*)?(\\d{1,3})$").matcher(texto);
        if (matcher.matches()) {
            return Short.valueOf(matcher.group(1));
        }
        return null;
    }

    /**
     * Busca el estado por su código tolerando mayúsculas y espacios: el
     * frontend manda "EN_NEGOCIACION"/"APROBADA" y no debe dar 404 si en la
     * BD el código está escrito distinto.
     */
    private EstadoCotizacion buscarEstado(String codigoEstado) {
        String normalizado = codigoEstado.trim();
        Optional<EstadoCotizacion> estado = estadoCotizacionRepository.findByCodigoEstado(normalizado);
        if (estado.isEmpty()) {
            estado = estadoCotizacionRepository.findFirstByCodigoEstadoIgnoreCase(normalizado);
        }
        return estado.orElseThrow(() -> new ResourceNotFoundException("Estado no encontrado: " + codigoEstado));
    }

    private List<CotizacionDetalleRequest> resolverLineas(CotizacionRequest request) {
        List<CotizacionDetalleRequest> lineas = request.getLineas() != null ? request.getLineas() : request.getDetalles();
        if (lineas == null || lineas.isEmpty()) {
            throw new BusinessException("La cotización debe tener al menos una línea");
        }
        return lineas;
    }

    private CotizacionResponse toResponse(Cotizacion c, Map<Long, TotalesCotizacionProjection> totalesPorCotizacion) {
        TotalesCotizacionProjection totales = totalesPorCotizacion.get(c.getIdCotizacion());
        return construirResponse(c, totales, null, false);
    }

    /** Respuesta completa (detalle): incluye líneas, seguimientos y adjuntos. */
    private CotizacionResponse toResponseDetalle(Cotizacion c) {
        TotalesCotizacionProjection totales = cotizacionRepository
                .findTotalesByCotizacion(c.getIdCotizacion())
                .orElse(null);
        return construirResponse(c, totales, null, true);
    }

    private CotizacionResponse toResponseDetalle(Cotizacion c, String codigoGenerado) {
        TotalesCotizacionProjection totales = cotizacionRepository
                .findTotalesByCotizacion(c.getIdCotizacion())
                .orElse(null);
        return construirResponse(c, totales, codigoGenerado, true);
    }

    private CotizacionResponse construirResponse(Cotizacion c, TotalesCotizacionProjection totales,
            String codigoGenerado, boolean conDetalle) {

        // Fallback a cero si proyecciones vienen nulas
        BigDecimal subtotal = (totales != null && totales.getSubtotal() != null) ? totales.getSubtotal() : BigDecimal.ZERO;
        BigDecimal igv = (totales != null && totales.getIgv() != null) ? totales.getIgv() : BigDecimal.ZERO;
        BigDecimal total = (totales != null && totales.getTotal() != null) ? totales.getTotal() : BigDecimal.ZERO;

        String nombreCliente = obtenerNombreCliente(c);
        String codigoEstado = c.getEstadoCotizacion() != null ? c.getEstadoCotizacion().getCodigoEstado() : null;
        String nombreEstado = c.getEstadoCotizacion() != null ? c.getEstadoCotizacion().getNombreEstado() : null;
        String codigoMoneda = c.getMoneda() != null ? c.getMoneda().getCodigoMoneda() : null;
        String simboloMoneda = c.getMoneda() != null ? c.getMoneda().getSimbolo() : "S/";

        CotizacionResponse.CotizacionResponseBuilder builder = CotizacionResponse.builder()
                .idCotizacion(c.getIdCotizacion())
                .id(c.getIdCotizacion())
                .codigoCotizacion(codigoGenerado != null ? codigoGenerado : c.getCodigoCotizacion())
                .codigo(codigoGenerado != null ? codigoGenerado : c.getCodigoCotizacion())
                .idCliente(c.getCliente() != null ? c.getCliente().getIdCliente() : null)
                .idContacto(c.getContacto() != null ? c.getContacto().getIdContacto() : null)
                .idUsuarioVendedor(c.getUsuarioVendedor() != null ? c.getUsuarioVendedor().getIdUsuario() : null)
                .idEstadoCotizacion(c.getEstadoCotizacion() != null ? c.getEstadoCotizacion().getIdEstadoCotizacion() : null)
                .idMoneda(c.getMoneda() != null ? c.getMoneda().getIdMoneda() : null)
                .idCondicionPago(c.getCondicionPago() != null ? c.getCondicionPago().getIdCondicionPago() : null)
                .idSectorEconomico(c.getSectorEconomico() != null ? c.getSectorEconomico().getIdSectorEconomico() : null)
                .clienteRazonSocial(nombreCliente)
                .clienteNombre(nombreCliente)
                .cliente(nombreCliente)
                .clienteEmail(obtenerCorreoCliente(c))
                .fechaCotizacion(c.getFechaCotizacion())
                .fechaValidez(c.getFechaValidez())
                .fechaCreacion(c.getDateCreate())
                .estado(nombreEstado)
                .estadoCodigo(codigoEstado)
                .estadoCotizacion(codigoEstado)
                .condicionPago(c.getCondicionPago() != null ? c.getCondicionPago().getNombreCondicion() : null)
                .subtotal(subtotal)
                .igv(igv)
                .total(total)
                // "moneda" lleva el CÓDIGO (PEN/USD/EUR): el frontend lo pasa a
                // Intl.NumberFormat({currency}) y un símbolo ahí revienta.
                .moneda(codigoMoneda)
                .monedaCodigo(codigoMoneda)
                .monedaSimbolo(simboloMoneda)
                .monedaNombre(c.getMoneda() != null ? c.getMoneda().getNombreMoneda() : null)
                .observaciones(c.getObservaciones());

        if (conDetalle) {
            builder.detalles(cotizacionDetalleRepository.findByCotizacion(c.getIdCotizacion()).stream()
                    .map(this::toLineaResponse).toList());
            builder.seguimientos(mapearSeguimientos(seguimientoCotizacionRepository.findByCotizacion(c.getIdCotizacion())));
            builder.adjuntos(adjuntoCotizacionRepository.findByCotizacion(c.getIdCotizacion()).stream()
                    .map(this::toAdjuntoResponse).toList());
        }

        return builder.build();
    }

    /**
     * Convierte los seguimientos a respuesta calculando el estado ANTERIOR de
     * cada evento: la consulta viene en orden descendente (más reciente
     * primero), así que el evento previo de la lista es el que tenía el
     * estado anterior. El frontend lo pinta como "Borrador → Aprobada".
     */
    private List<SeguimientoCotizacionResponse> mapearSeguimientos(List<SeguimientoCotizacion> seguimientos) {
        List<SeguimientoCotizacionResponse> respuesta = new ArrayList<>(seguimientos.size());
        for (int i = 0; i < seguimientos.size(); i++) {
            SeguimientoCotizacion actual = seguimientos.get(i);
            String estadoAnterior = (i + 1 < seguimientos.size())
                    ? codigoEstadoDe(seguimientos.get(i + 1))
                    : null;
            respuesta.add(toSeguimientoResponse(actual, estadoAnterior));
        }
        return respuesta;
    }

    private String codigoEstadoDe(SeguimientoCotizacion s) {
        return s.getEstadoCotizacion() != null ? s.getEstadoCotizacion().getCodigoEstado() : null;
    }

    /**
     * Correo del cliente para las notificaciones: primero el del contacto
     * asignado a la cotización y, si no hay, el del tercero (razón social).
     */
    private String obtenerCorreoCliente(Cotizacion c) {
        try {
            if (c.getContacto() != null && c.getContacto().getCorreo() != null
                    && !c.getContacto().getCorreo().isBlank()) {
                return c.getContacto().getCorreo();
            }
            if (c.getCliente() != null && c.getCliente().getTercero() != null
                    && c.getCliente().getTercero().getCorreo() != null
                    && !c.getCliente().getTercero().getCorreo().isBlank()) {
                return c.getCliente().getTercero().getCorreo();
            }
        } catch (Exception e) {
            log.warn("Error obteniendo correo del cliente para cotización {}: {}",
                    c.getIdCotizacion(), e.getMessage());
        }
        return null;
    }

    private SeguimientoCotizacionResponse toSeguimientoResponse(SeguimientoCotizacion s, String estadoAnterior) {
        String codigoEstado = s.getEstadoCotizacion() != null ? s.getEstadoCotizacion().getCodigoEstado() : null;
        String nombreEstado = s.getEstadoCotizacion() != null ? s.getEstadoCotizacion().getNombreEstado() : null;
        String usuario = s.getUsuario() != null ? s.getUsuario().getNombreUsuario() : null;
        return SeguimientoCotizacionResponse.builder()
                .idSeguimiento(s.getIdSeguimiento())
                .id(s.getIdSeguimiento())
                .idEstadoCotizacion(s.getEstadoCotizacion() != null ? s.getEstadoCotizacion().getIdEstadoCotizacion() : null)
                .estado(nombreEstado)
                .estadoCodigo(codigoEstado)
                .estadoNuevo(codigoEstado)
                .estadoAnterior(estadoAnterior)
                .usuario(usuario)
                .usuarioNombre(usuario)
                .fechaEvento(s.getFechaEvento())
                .fecha(s.getFechaEvento())
                .comentario(s.getComentario())
                .observaciones(s.getComentario())
                .build();
    }

    private AdjuntoCotizacionResponse toAdjuntoResponse(AdjuntoCotizacion a) {
        return AdjuntoCotizacionResponse.builder()
                .idAdjunto(a.getIdAdjunto())
                .id(a.getIdAdjunto())
                .nombreArchivo(a.getNombreArchivo())
                .urlArchivo(a.getUrlArchivo())
                .urlCloudinary(a.getUrlArchivo())
                .tipoArchivo(extensionDe(a.getNombreArchivo()))
                // La tabla adjunto_cotizacion no guarda el tamaño: se
                // devuelve 0 para no dejar el campo vacío (el frontend lo
                // formatea sin validar y un null lo tumba).
                .tamanio(0L)
                .fechaSubida(a.getDateCreate())
                .subidoPor(a.getUserCreate())
                .build();
    }

    private String extensionDe(String nombreArchivo) {
        if (nombreArchivo == null) {
            return null;
        }
        int punto = nombreArchivo.lastIndexOf('.');
        return (punto >= 0 && punto < nombreArchivo.length() - 1)
                ? nombreArchivo.substring(punto + 1).toLowerCase()
                : null;
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

    // Obtiene razón social o documento con fallback seguro
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
        } catch (Exception e) {
            log.warn("Error obteniendo nombre de cliente para cotización {}: {}", c.getIdCotizacion(), e.getMessage());
        }
        return "Cliente";
    }

    private CotizacionDetalleResponse toLineaResponse(CotizacionDetalle d) {
        String nombreProducto = d.getProducto() != null ? d.getProducto().getNombreProducto() : null;
        String nombreServicio = d.getServicio() != null ? d.getServicio().getNombreServicio() : null;
        // importe_linea lo calcula la BD; si viniera nulo se recalcula aquí
        // para que el frontend no muestre 0 en el subtotal de la línea.
        BigDecimal importe = d.getImporteLinea() != null ? d.getImporteLinea() : calcularImporteLinea(d);
        return CotizacionDetalleResponse.builder()
                .idCotizacionDetalle(d.getIdCotizacionDetalle())
                .id(d.getIdCotizacionDetalle())
                .idProducto(d.getProducto() != null ? d.getProducto().getIdProducto() : null)
                .productoNombre(nombreProducto)
                .producto(nombreProducto)
                .idServicio(d.getServicio() != null ? d.getServicio().getIdServicio() : null)
                .servicioNombre(nombreServicio)
                .servicio(nombreServicio)
                .idUnidadMedida(d.getUnidadMedida() != null ? d.getUnidadMedida().getIdUnidadMedida() : null)
                .unidadMedida(d.getUnidadMedida() != null ? d.getUnidadMedida().getNombreUnidad() : null)
                .descripcionPersonalizada(d.getDescripcionPersonalizada())
                .descripcion(d.getDescripcionPersonalizada())
                .cantidad(d.getCantidad())
                .precioUnitario(d.getPrecioUnitario())
                .descuentoUnitario(d.getDescuentoUnitario())
                .importeLinea(importe)
                .subtotal(importe)
                .build();
    }

    /** Importe de la línea: cantidad x (precio - descuento). */
    private BigDecimal calcularImporteLinea(CotizacionDetalle d) {
        BigDecimal cantidad = d.getCantidad() != null ? d.getCantidad() : BigDecimal.ZERO;
        BigDecimal precio = d.getPrecioUnitario() != null ? d.getPrecioUnitario() : BigDecimal.ZERO;
        BigDecimal descuento = d.getDescuentoUnitario() != null ? d.getDescuentoUnitario() : BigDecimal.ZERO;
        return cantidad.multiply(precio.subtract(descuento)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Datos que se arman dentro de la transacción previa al envío del correo. */
    private record DatosEnvio(String nombreCliente, String cuerpoTexto, String cuerpoHtml, Usuario usuario) {}
}
