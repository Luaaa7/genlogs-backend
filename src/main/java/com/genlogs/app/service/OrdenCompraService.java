package com.genlogs.app.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.genlogs.app.dto.CambiarEstadoOrdenCompraRequest;
import com.genlogs.app.dto.ConfirmarOcRequest;
import com.genlogs.app.dto.OrdenCompraRequest;
import com.genlogs.app.dto.PaginaResponse;
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
import com.genlogs.app.repository.FacturacionRepository;
import com.genlogs.app.repository.EstadoOrdenCompraRepository;
import com.genlogs.app.repository.OrdenCompraRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.util.PdfUtils;

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
    private final FacturacionRepository facturacionRepository;
    private final LectorOcPdf lectorOcPdf;

    @Transactional(readOnly = true)
    public PaginaResponse<OrdenCompraResponse> listar(String estadoCodigo, Long idCotizacion, int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 200);
        Page<OrdenCompra> resultado = ordenCompraRepository.buscar(
                estadoCodigo == null ? "" : estadoCodigo.trim(),
                idCotizacion == null ? 0L : idCotizacion,
                PageRequest.of(p, s));
        Map<Long, BigDecimal> totales = totalesDe(resultado.getContent());
        return PaginaResponse.of(resultado.map(oc -> aRespuesta(oc, totales.get(oc.getCotizacion().getIdCotizacion()))));
    }

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
        List<OrdenCompra> ordenes = ordenCompraRepository.findByCliente(idCliente);
        Map<Long, BigDecimal> totales = totalesDe(ordenes);
        return ordenes.stream()
                .map(oc -> aRespuesta(oc, totales.get(oc.getCotizacion().getIdCotizacion())))
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

    /**
     * Al aprobar una cotización se registra sola su orden de compra (estado RECIBIDA),
     * con el siguiente número correlativo (0031-10). Si ya tiene una, no hace nada.
     */
    @Transactional
    public OrdenCompraResponse crearAutomatica(Long idCotizacion) {
        Cotizacion cotizacion = cotizacionRepository.findById(idCotizacion)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));
        if (!"APROBADA".equals(cotizacion.getEstadoCotizacion().getCodigoEstado())
                || ordenCompraRepository.existsByCotizacion_IdCotizacionAndStatus(idCotizacion, "A")) {
            return null;
        }
        EstadoOrdenCompra estadoInicial = estadoOrdenCompraRepository.findByCodigoEstado("RECIBIDA")
                .orElseThrow(() -> new BusinessException("No existe el estado RECIBIDA configurado"));
        Usuario usuario = usuarioActual();

        OrdenCompra ordenCompra = OrdenCompra.builder()
                .cotizacion(cotizacion)
                .estadoOrdenCompra(estadoInicial)
                .condicionPago(cotizacion.getCondicionPago())
                .numeroOrdenCompra(siguienteNumero())
                .observaciones("Registrada automáticamente al aprobar la cotización "
                        + cotizacion.getCodigoCotizacion() + ".")
                .build();
        ordenCompra.setUserCreate(usuario.getNombreUsuario());
        ordenCompra.setProcessCreate("ALTA_AUTOMATICA_ORDEN_COMPRA");
        return toResponse(ordenCompraRepository.save(ordenCompra));
    }

    /** Siguiente número con el formato de las órdenes (0031-10): correlativo de 4 dígitos + "-10". */
    private String siguienteNumero() {
        java.util.regex.Pattern formato = java.util.regex.Pattern.compile("^(\\d{4})-\\d+$");
        int mayor = ordenCompraRepository.todosLosNumeros().stream()
                .map(n -> n == null ? null : formato.matcher(n.trim()))
                .filter(m -> m != null && m.matches())
                .mapToInt(m -> Integer.parseInt(m.group(1)))
                .max()
                .orElse(0);
        String numero = String.format("%04d-10", mayor + 1);
        while (ordenCompraRepository.findByNumeroOrdenCompra(numero).isPresent()) {
            mayor++;
            numero = String.format("%04d-10", mayor + 1);
        }
        return numero;
    }

    @Transactional
    public OrdenCompraResponse cambiarEstado(Long idOrdenCompra, CambiarEstadoOrdenCompraRequest request) {
        OrdenCompra ordenCompra = obtenerOLanzar(idOrdenCompra);

        EstadoOrdenCompra nuevoEstado = estadoOrdenCompraRepository.findByCodigoEstado(request.getCodigoEstado())
                .orElseThrow(() -> new ResourceNotFoundException("Estado no encontrado: " + request.getCodigoEstado()));

        // Atendida y Anulada son finales: solo un ADMINISTRADOR puede corregirlas
        // (por ejemplo, si se marcó por error).
        if (Boolean.TRUE.equals(ordenCompra.getEstadoOrdenCompra().getEsFinal()) && !esAdministrador()) {
            throw new BusinessException(
                    "La orden ya está " + ordenCompra.getEstadoOrdenCompra().getNombreEstado().toLowerCase()
                            + ". Solo un administrador puede corregir su estado.");
        }

        // Una orden que ya tiene factura vigente no puede anularse ni volver atrás:
        // primero hay que anular la factura.
        boolean conFactura = facturacionRepository
                .existsByOrdenCompra_IdOrdenCompraAndEstadoFacturacion_CodigoEstadoNot(idOrdenCompra, "ANULADA");
        if (conFactura && !"ATENDIDA".equalsIgnoreCase(nuevoEstado.getCodigoEstado())) {
            throw new BusinessException(
                    "Esta orden ya tiene una factura. Para cambiar su estado, primero anula la factura.");
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

    private static boolean esAdministrador() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMINISTRADOR".equals(a.getAuthority()));
    }

    public OrdenCompraResponse agregarArchivo(Long idOrdenCompra, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("El archivo está vacío o no es válido");
        }
        OrdenCompra ordenCompra = obtenerOLanzar(idOrdenCompra);
        String urlAnterior = ordenCompra.getUrlArchivo();
        byte[] bytes = bytesDe(archivo);

        // La subida queda fuera de la transacción. Los bytes se leen una sola vez.
        String urlNueva = cloudinaryService.subirDocumento(
                new ArchivoEnMemoria(archivo.getOriginalFilename(), archivo.getContentType(), bytes),
                "genlogs/ordenes-compra");
        LecturaOc lectura = esPdf(bytes) ? lectorOcPdf.leer(bytes) : LecturaOc.vacia();

        OrdenCompraResponse response = guardarArchivoYLectura(idOrdenCompra, urlNueva, lectura);

        if (urlAnterior != null && !urlAnterior.isBlank() && !urlAnterior.equals(urlNueva)) {
            cloudinaryService.eliminarPorUrl(urlAnterior, CloudinaryService.TIPO_DOCUMENTO);
        }
        return response;
    }

    /**
     * La URL y lo leído se escriben en un solo save. Sin @Transactional:
     * agregarArchivo llama este método sobre this y el proxy no lo aplicaría.
     */
    private OrdenCompraResponse guardarArchivoYLectura(Long idOrdenCompra, String url, LecturaOc lectura) {
        OrdenCompra ordenCompra = obtenerOLanzar(idOrdenCompra);
        LecturaOc leida = lectura == null ? LecturaOc.vacia() : lectura;
        ordenCompra.setUrlArchivo(url);
        ordenCompra.setNumeroOcCliente(leida.numeroOc());
        ordenCompra.setMontoOcCliente(leida.monto());
        ordenCompra.setMonedaOcCliente(codigoMonedaOc(leida.moneda()));
        ordenCompra.setFechaEmisionCliente(leida.fechaEmision());
        ordenCompra.setOcLeidaAuto(leida.hayAlgo());
        ordenCompra.setOcConfirmadaPor(null);

        Usuario usuario = usuarioActual();
        ordenCompra.setUserUpdate(usuario.getNombreUsuario());
        ordenCompra.setProcessUpdate("ADJUNTAR_ARCHIVO_ORDEN_COMPRA");
        ordenCompra.setDateUpdate(LocalDateTime.now());
        ordenCompra = ordenCompraRepository.save(ordenCompra);
        return toResponse(ordenCompra);
    }

    @Transactional
    public OrdenCompraResponse confirmar(Long idOrdenCompra, ConfirmarOcRequest request) {
        OrdenCompra ordenCompra = obtenerOLanzar(idOrdenCompra);
        if (Boolean.TRUE.equals(ordenCompra.getEstadoOrdenCompra().getEsFinal())) {
            throw new BusinessException(
                    "La orden ya está " + ordenCompra.getEstadoOrdenCompra().getNombreEstado().toLowerCase()
                            + ". La OC del cliente solo se corrige mientras no esté cerrada.");
        }
        ordenCompra.setNumeroOcCliente(request.getNumeroOc().trim());
        ordenCompra.setMontoOcCliente(request.getMonto());
        if (request.getFechaEmision() != null) {
            ordenCompra.setFechaEmisionCliente(request.getFechaEmision());
        }
        if (request.getMoneda() != null) {
            ordenCompra.setMonedaOcCliente(codigoMonedaOc(request.getMoneda()));
        }
        Usuario usuario = usuarioActual();
        ordenCompra.setOcConfirmadaPor(usuario);
        ordenCompra.setUserUpdate(usuario.getNombreUsuario());
        ordenCompra.setProcessUpdate("CONFIRMAR_OC_CLIENTE");
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

    private Map<Long, BigDecimal> totalesDe(List<OrdenCompra> ordenes) {
        if (ordenes == null || ordenes.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = ordenes.stream()
                .map(oc -> oc.getCotizacion().getIdCotizacion())
                .distinct()
                .toList();
        Map<Long, BigDecimal> mapa = new HashMap<>();
        for (CotizacionRepository.TotalesCotizacionPorIdProjection total
                : cotizacionRepository.findTotalesPorCotizaciones(ids)) {
            mapa.put(total.getIdCotizacion(), total.getTotal());
        }
        return mapa;
    }

    private OrdenCompraResponse toResponse(OrdenCompra oc) {
        BigDecimal total = cotizacionRepository.findTotalesByCotizacion(oc.getCotizacion().getIdCotizacion())
                .map(t -> t.getTotal())
                .orElse(null);
        return aRespuesta(oc, total);
    }

    private OrdenCompraResponse aRespuesta(OrdenCompra oc, BigDecimal total) {
        Long idCotizacion = oc.getCotizacion().getIdCotizacion();
        OrdenCompraResponse respuesta = OrdenCompraResponse.builder()
                .idOrdenCompra(oc.getIdOrdenCompra())
                .idCotizacion(idCotizacion)
                .codigoCotizacion(oc.getCotizacion().getCodigoCotizacion())
                .numeroOrdenCompra(oc.getNumeroOrdenCompra())
                .clienteRazonSocial(oc.getCotizacion().getCliente().getTercero().getRazonSocial())
                .cliente(oc.getCotizacion().getCliente().getTercero().getRazonSocial())
                .estadoCodigo(oc.getEstadoOrdenCompra().getCodigoEstado())
                .fechaEmisionCliente(oc.getFechaEmisionCliente())
                .fechaRecepcion(oc.getFechaRecepcion())
                .estado(oc.getEstadoOrdenCompra().getNombreEstado())
                .urlArchivo(oc.getUrlArchivo())
                .observaciones(oc.getObservaciones())
                .total(total == null ? BigDecimal.ZERO : total)
                .moneda(PdfUtils.simboloMoneda(oc.getCotizacion().getMoneda()))
                .numeroOcCliente(oc.getNumeroOcCliente())
                .montoOcCliente(oc.getMontoOcCliente())
                .ocLeidaAuto(oc.isOcLeidaAuto())
                .ocConfirmada(oc.getOcConfirmadaPor() != null)
                .build();
        aplicarAviso(respuesta, oc, total);
        return respuesta;
    }

    /** Subir, confirmar y los GET usan este mismo cálculo. */
    private static void aplicarAviso(OrdenCompraResponse respuesta, OrdenCompra oc, BigDecimal total) {
        String codigo = codigoMonedaOc(oc.getMonedaOcCliente());
        respuesta.setMonedaLeida(codigo);
        respuesta.setAvisoMonto(avisoMonto(
                oc.getMontoOcCliente(),
                codigo,
                total,
                PdfUtils.codigoMoneda(oc.getCotizacion().getMoneda())));
    }

    /** Mayúsculas y 3 letras. CHAR(3) puede traer espacios; otro texto se descarta. */
    static String codigoMonedaOc(String codigo) {
        if (codigo == null) {
            return null;
        }
        String limpio = codigo.trim().toUpperCase(Locale.ROOT);
        return limpio.matches("[A-Z]{3}") ? limpio : null;
    }

    static String avisoMonto(BigDecimal montoOc, String monedaLeida, BigDecimal total, String monedaCotizacion) {
        if (montoOc == null) {
            return null;
        }
        if (monedaLeida == null) {
            return "No se pudo determinar la moneda del PDF; verifica que coincida con la de la cotización.";
        }
        String cotizacion = monedaCotizacion == null ? "" : monedaCotizacion.trim().toUpperCase(Locale.ROOT);
        if (cotizacion.isEmpty() || !monedaLeida.equalsIgnoreCase(cotizacion)) {
            return "La OC está en " + monedaLeida + " y la cotización en "
                    + (cotizacion.isEmpty() ? "sin moneda" : cotizacion) + "; no se compara el monto.";
        }
        if (total == null) {
            return "No se pudo comparar el monto: la cotización no tiene total.";
        }
        if (montoOc.subtract(total).abs().compareTo(new BigDecimal("0.01")) > 0) {
            return "El monto de la OC (" + montoOc.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                    + ") no coincide con el total de la cotización ("
                    + total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + ").";
        }
        return null;
    }

    private static byte[] bytesDe(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer el archivo");
        }
    }

    private static boolean esPdf(byte[] bytes) {
        return bytes.length >= 5
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F'
                && bytes[4] == '-';
    }

    /** Multipart ya leído: Cloudinary no vuelve a tocar el archivo original. */
    private static final class ArchivoEnMemoria implements MultipartFile {
        private final String nombre;
        private final String tipo;
        private final byte[] bytes;

        private ArchivoEnMemoria(String nombre, String tipo, byte[] bytes) {
            this.nombre = nombre;
            this.tipo = tipo;
            this.bytes = bytes;
        }

        @Override public String getName() { return "archivo"; }
        @Override public String getOriginalFilename() { return nombre; }
        @Override public String getContentType() { return tipo; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes; }
        @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(bytes); }
        @Override public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }
}