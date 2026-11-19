package com.genlogs.app.service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.CoberturaPublicaResponse;
import com.genlogs.app.dto.ConsultaDocumentoResponse;
import com.genlogs.app.dto.CrearSolicitudRequest;
import com.genlogs.app.dto.CrearSolicitudResponse;
import com.genlogs.app.dto.PedidoItemResponse;
import com.genlogs.app.dto.PedidoResponse;
import com.genlogs.app.dto.ProductoPublicoResponse;
import com.genlogs.app.dto.RegistroClienteRequest;
import com.genlogs.app.dto.RegistroClienteResponse;
import com.genlogs.app.enums.EstadoSolicitud;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.enums.TipoEventoSolicitud;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.EstadoNoPermitidoException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Auditable;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.EmpresaMinera;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.ProductoImagen;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.SectorEconomico;
import com.genlogs.app.model.SolicitudCotizacion;
import com.genlogs.app.model.SolicitudItem;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.TipoDocumento;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.EmpresaMineraRepository;
import com.genlogs.app.repository.ProductoImagenRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.SectorEconomicoRepository;
import com.genlogs.app.repository.SolicitudCotizacionRepository;
import com.genlogs.app.repository.TerceroRepository;
import com.genlogs.app.repository.TipoDocumentoRepository;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PortalClienteService {

    private static final Logger log = LoggerFactory.getLogger(PortalClienteService.class);
    private static final int LIMITE_CATALOGO = 24;
    private static final String ROL_CLIENTE = "CLIENTE";
    private static final String ESTADO_INICIAL = "PENDIENTE";
    private static final int MAX_NOMBRE_USUARIO = 50;
    /** RUC de empresa (20) o de persona con negocio (10), igual que FactilizaService. */
    private static final Pattern RUC_VALIDO = Pattern.compile("^(10|20)\\d{9}$");
    private static final Pattern DNI_VALIDO = Pattern.compile("^\\d{8}$");
    private static final String DOCUMENTO_INVALIDO =
            "Ingresa un RUC de 11 dígitos (empieza con 10 o 20) o un DNI de 8 dígitos.";
    private static final String SIN_DOMICILIO =
            "No pudimos ubicar el domicilio fiscal de tu RUC. Escríbenos y te damos acceso.";
    private static final String SECTOR_CAPTACION = "MINERIA";
    /** Marca del usuario que pidió acceso a una empresa que ya es cliente (lo usa UsuarioService.activar). */
    public static final String PROCESO_PENDIENTE = "REGISTRO_PORTAL_PENDIENTE";
    private static final java.util.Set<String> ESTADOS_CANCELABLES = java.util.Set.of(
            EstadoSolicitud.PENDIENTE.name(), EstadoSolicitud.EN_REVISION.name());

    private final ProductoRepository productoRepository;
    private final ProductoImagenRepository productoImagenRepository;
    private final EmpresaMineraRepository empresaMineraRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final SolicitudCotizacionRepository solicitudRepository;
    private final PasswordEncoder passwordEncoder;
    private final LimitadorRegistroPortal limitadorRegistro;
    private final BitacoraSolicitud bitacora;
    private final AuthService authService;
    private final FactilizaService factilizaService;
    private final TerceroRepository terceroRepository;
    private final TipoDocumentoRepository tipoDocumentoRepository;
    private final DistritoRepository distritoRepository;
    private final SectorEconomicoRepository sectorEconomicoRepository;
    private final NotificacionService notificacionService;

    @Transactional(readOnly = true)
    public List<ProductoPublicoResponse> catalogo() {
        List<Producto> productos = productoRepository.findByStatusAndVisibleWeb(
                "A", true, PageRequest.of(0, LIMITE_CATALOGO, Sort.by("nombreProducto").ascending()));
        Map<Long, String> imagenes = imagenesPrincipales(productos);
        List<ProductoPublicoResponse> respuesta = new ArrayList<>();
        for (Producto producto : productos) {
            respuesta.add(ProductoPublicoResponse.builder()
                    .id(producto.getIdProducto())
                    .nombre(producto.getNombreProducto())
                    .descripcion(vacioComoNull(producto.getDescripcion()))
                    .categoria(producto.getCategoriaProducto().getNombreCategoria())
                    .unidad(producto.getUnidadMedida().getNombreUnidad())
                    .imagenUrl(imagenes.get(producto.getIdProducto()))
                    .build());
        }
        return respuesta;
    }

    @Transactional(readOnly = true)
    public List<CoberturaPublicaResponse> cobertura() {
        List<CoberturaPublicaResponse> respuesta = new ArrayList<>();
        for (EmpresaMinera empresa : empresaMineraRepository.findByStatus("A")) {
            var tercero = empresa.getCliente().getTercero();
            var distrito = tercero.getDistrito();
            if (distrito == null) {
                // D-15: cliente extranjero, sin distrito del catálogo; se muestra su ubicación en texto.
                respuesta.add(CoberturaPublicaResponse.builder()
                        .id(empresa.getIdEmpresaMinera())
                        .nombre(empresa.getNombreUnidadMinera())
                        .region(tercero.getRegionExtranjero())
                        .descripcion(tercero.getCiudadExtranjero())
                        .build());
                continue;
            }
            var provincia = distrito.getProvincia();
            respuesta.add(CoberturaPublicaResponse.builder()
                    .id(empresa.getIdEmpresaMinera())
                    .nombre(empresa.getNombreUnidadMinera())
                    .region(provincia.getDepartamento().getNombreDepartamento())
                    .descripcion(distrito.getNombreDistrito() + ", " + provincia.getNombreProvincia())
                    .build());
        }
        return respuesta;
    }

    /**
     * Registro del portal (D-10). El acceso inmediato es solo para la empresa nueva:
     * <ul>
     *   <li>Si el RUC o el DNI no es cliente, se valida en Factiliza, se da de alta la
     *       persona o empresa (captación de mineras, proceso 3) y el usuario nace activo
     *       con su token, así sigue sin esperar (y la landing envía su pedido guardado, D-30).</li>
     *   <li>Si el documento ya es cliente, el usuario nuevo queda PENDIENTE (status 'I', sin
     *       token) hasta que lo active el Jefe Administrativo. El documento es público y el
     *       portal muestra los pedidos, precios y despachos de la empresa y deja aprobar
     *       cotizaciones: nadie debe entrar a eso solo por conocer el número. Se avisa al
     *       Jefe Administrativo y, por correo, a los usuarios activos de la empresa.
     *       Ese caso no consulta Factiliza ni consume el límite de intentos.</li>
     * </ul>
     */
    @Transactional
    public RegistroClienteResponse registrar(RegistroClienteRequest request) {
        String documento = soloDigitos(request.getDocumento());
        String correo = request.getCorreo().trim();
        boolean ruc = RUC_VALIDO.matcher(documento).matches();
        boolean dni = DNI_VALIDO.matcher(documento).matches();
        if (!ruc && !dni) {
            throw new BusinessException(DOCUMENTO_INVALIDO);
        }
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new BusinessException("Ese correo ya tiene una cuenta. Ingresa al portal con tu correo y contraseña.");
        }
        Rol rol = rolRepository.findByNombreRolIgnoreCase(ROL_CLIENTE).orElseThrow(() -> {
            log.error("Registro de portal rechazado: no existe el rol CLIENTE. Ejecuta la migración.");
            return new BusinessException("El registro no está disponible en este momento. Inténtalo más tarde.");
        });

        Cliente existente = clienteRepository.findByNumeroDocumento(documento).orElse(null);
        boolean empresaNueva = existente == null;
        Cliente cliente = empresaNueva ? altaClienteNuevo(documento, dni, correo, request) : existente;
        if (!"ACTIVO".equals(cliente.getSituacion())) {
            throw new BusinessException("Tu empresa no tiene el acceso al portal habilitado. Escríbenos para ayudarte.");
        }

        String nombreUsuario = nombreUsuarioDisponible(correo);
        if (nombreUsuario == null) {
            throw new BusinessException("No pudimos crear tu usuario. Inténtalo de nuevo en unos minutos.");
        }
        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        usuario.setCliente(cliente);
        usuario.setNombreUsuario(nombreUsuario);
        usuario.setNombres(request.getNombreContacto().trim());
        usuario.setCorreo(correo);
        usuario.setIniciales(iniciales(request.getNombreContacto(), request.getRazonSocial()));
        usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        usuario.setBloqueado(false);
        usuario.setIntentosFallidos((short) 0);
        // Empresa que ya es cliente: queda pendiente (status 'I') hasta que la active el Jefe Administrativo.
        usuario.setStatus(empresaNueva ? "A" : "I");
        usuario.setUserCreate("portal");
        usuario.setProcessCreate(empresaNueva ? "REGISTRO_PORTAL" : PROCESO_PENDIENTE);
        usuario.setDateCreate(LocalDateTime.now());
        usuarioRepository.saveAndFlush(usuario);

        if (!empresaNueva) {
            avisarAccesoPendiente(cliente, usuario);
            return RegistroClienteResponse.pendiente(usuario.getNombres());
        }
        return RegistroClienteResponse.conAcceso(authService.emitirToken(usuario), usuario.getNombres(), nombreUsuario);
    }

    /**
     * Aviso de un acceso pendiente: en la campanita del Jefe Administrativo (que lo
     * activa desde Usuarios) y por correo a los usuarios activos de la empresa, para
     * que sepan si alguien que no conocen pidió entrar con su documento. Si el aviso falla,
     * el registro sigue: el usuario ya quedó pendiente y no ve nada.
     */
    private void avisarAccesoPendiente(Cliente cliente, Usuario nuevo) {
        String empresa = cliente.getTercero().getRazonSocial();
        String numero = cliente.getTercero().getNumeroDocumento();
        try {
            notificacionService.avisarRol(RolSistema.JEFE_ADMINISTRATIVO, TipoAviso.ACCESO_PORTAL_PENDIENTE,
                    "Acceso al portal pendiente",
                    nuevo.getNombres() + " (" + nuevo.getCorreo() + ") pidió acceso al portal para "
                            + empresa + " (documento " + numero + "). Revísalo y actívalo en Usuarios si corresponde.",
                    "/usuarios", false);
            for (Usuario colega : usuarioRepository.findByCliente_IdClienteAndStatus(cliente.getIdCliente(), "A")) {
                notificacionService.enviarCorreoCliente(colega.getCorreo(), colega.getNombres(),
                        "Pidieron acceso al portal de GENLOGS para " + empresa,
                        nuevo.getNombres() + " (" + nuevo.getCorreo() + ") pidió acceso al portal de GENLOGS para "
                                + empresa + ". No tendrá acceso hasta que GENLOGS lo revise. "
                                + "Si no reconoces a esta persona, respóndenos para bloquear la solicitud.");
            }
        } catch (RuntimeException ex) {
            log.warn("No se pudo avisar el acceso pendiente del usuario {}: {}", nuevo.getIdUsuario(), ex.getMessage());
        }
    }

    /**
     * Documento que no está en la base: primero el límite de intentos, después Factiliza.
     * Sin distrito del catálogo no se inserta el tercero: id_distrito puede rechazar null
     * y eso no debe terminar en un 500.
     */
    private Cliente altaClienteNuevo(String documento, boolean dni, String correo, RegistroClienteRequest request) {
        limitadorRegistro.verificar(documento, correo);
        ConsultaDocumentoResponse datos;
        try {
            datos = factilizaService.consultarPorNumero(documento);
        } catch (BusinessException ex) {
            log.info("Registro de portal rechazado: no se pudo validar el documento {} ({})", documento, ex.getMessage());
            throw new BusinessException(dni
                    ? "No pudimos validar tu DNI. Revisa el número o escríbenos para ayudarte."
                    : "No pudimos validar tu RUC en SUNAT. Revisa el número o escríbenos para ayudarte.");
        }
        if (datos == null || datos.getIdDistrito() == null) {
            log.info("Registro de portal rechazado: el documento {} no tiene un distrito del catálogo", documento);
            throw new BusinessException(SIN_DOMICILIO);
        }
        String codigoTipo = dni ? "DNI" : "RUC";
        TipoDocumento tipo = tipoDocumentoRepository.findByCodigoTipo(codigoTipo)
                .orElseThrow(() -> new BusinessException("El registro no está disponible en este momento. Inténtalo más tarde."));
        Distrito distrito = distritoRepository.findById(datos.getIdDistrito())
                .orElseThrow(() -> new BusinessException(SIN_DOMICILIO));

        Tercero tercero = new Tercero();
        tercero.setTipoDocumento(tipo);
        tercero.setNumeroDocumento(documento);
        tercero.setRazonSocial(recortar(razonSocialDe(datos, dni, request), 200));
        tercero.setDistrito(distrito);
        tercero.setDireccion(recortar(datos.getDireccion(), 255));
        tercero.setTelefono(vacioComoNull(request.getTelefono()));
        tercero.setCorreo(correo);
        auditarAlta(tercero);
        tercero = terceroRepository.save(tercero);

        Cliente cliente = new Cliente();
        cliente.setTercero(tercero);
        cliente.setSectorEconomico(sectorDeCaptacion());
        cliente.setSituacion("ACTIVO");
        auditarAlta(cliente);
        log.info("Cliente nuevo desde el registro del portal: {} {}", codigoTipo, documento);
        return clienteRepository.save(cliente);
    }

    /** RUC: razón social de Factiliza. DNI: nombres y apellidos. En simulado, el RUC usa lo escrito. */
    private static String razonSocialDe(ConsultaDocumentoResponse datos, boolean dni, RegistroClienteRequest request) {
        if (dni) {
            String completo = unir(datos.getNombres(), datos.getApellidoPaterno(), datos.getApellidoMaterno());
            if (completo.isBlank()) {
                completo = datos.getRazonSocial() == null ? "" : datos.getRazonSocial().trim();
            }
            return completo.isBlank() ? request.getRazonSocial().trim() : completo;
        }
        if (datos.isSimulado() || datos.getRazonSocial() == null || datos.getRazonSocial().isBlank()) {
            return request.getRazonSocial().trim();
        }
        return datos.getRazonSocial().trim();
    }

    private static String unir(String... partes) {
        StringBuilder texto = new StringBuilder();
        for (String parte : partes) {
            if (parte == null || parte.isBlank()) {
                continue;
            }
            if (!texto.isEmpty()) {
                texto.append(' ');
            }
            texto.append(parte.trim());
        }
        return texto.toString();
    }

    /** Las empresas que se registran solas entran al sector "Minería"; el Jefe Administrativo lo corrige si hace falta. */
    private SectorEconomico sectorDeCaptacion() {
        return sectorEconomicoRepository.findByStatus("A").stream()
                .filter(s -> SECTOR_CAPTACION.equalsIgnoreCase(sinTildes(s.getNombreSector()).trim()))
                .findFirst()
                .orElseGet(() -> {
                    SectorEconomico sector = new SectorEconomico();
                    sector.setNombreSector("Minería");
                    sector.setDescripcion("Creado por el registro del portal");
                    auditarAlta(sector);
                    return sectorEconomicoRepository.save(sector);
                });
    }

    private void auditarAlta(Auditable entidad) {
        entidad.setStatus("A");
        entidad.setUserCreate("portal");
        entidad.setProcessCreate("REGISTRO_PORTAL");
        entidad.setDateCreate(LocalDateTime.now());
    }

    private static String recortar(String valor, int maximo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String v = valor.trim();
        return v.length() > maximo ? v.substring(0, maximo) : v;
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> pedidos() {
        Usuario usuario = exigirCliente();
        return solicitudRepository
                .findByCliente_IdClienteAndStatusOrderByDateCreateDesc(usuario.getCliente().getIdCliente(), "A")
                .stream()
                .map(this::aPedido)
                .toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponse pedido(Long id) {
        Usuario usuario = exigirCliente();
        SolicitudCotizacion solicitud = solicitudRepository
                .findByIdSolicitudAndCliente_IdClienteAndStatus(
                        id, usuario.getCliente().getIdCliente(), "A")
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
        return aPedido(solicitud);
    }

    @Transactional
    public CrearSolicitudResponse crearSolicitud(CrearSolicitudRequest request) {
        Usuario usuario = exigirCliente();
        if (usuario.getStatus() == null || !usuario.getStatus().trim().startsWith("A")) {
            throw new AccessDeniedException("El acceso del portal todavía no está habilitado");
        }

        SolicitudCotizacion solicitud = new SolicitudCotizacion();
        solicitud.setCliente(usuario.getCliente());
        solicitud.setUsuario(usuario);
        solicitud.setCodigoSolicitud(nuevoCodigo());
        solicitud.setAsunto(request.getAsunto().trim());
        solicitud.setDescripcion(request.getDescripcion().trim());
        solicitud.setEstadoSolicitud(ESTADO_INICIAL);
        solicitud.setUserCreate(usuario.getNombreUsuario());
        solicitud.setProcessCreate("SOLICITUD_PORTAL");
        solicitud.setDateCreate(LocalDateTime.now());
        if (request.getItems() != null) {
            for (CrearSolicitudRequest.Item item : request.getItems()) {
                SolicitudItem linea = new SolicitudItem();
                linea.setDescripcion(item.getDescripcion().trim());
                linea.setCantidad(item.getCantidad());
                linea.setUnidad(vacioComoNull(item.getUnidad()));
                linea.setUserCreate(usuario.getNombreUsuario());
                linea.setProcessCreate("SOLICITUD_PORTAL");
                linea.setDateCreate(LocalDateTime.now());
                solicitud.agregarItem(linea);
            }
        }
        SolicitudCotizacion guardada = solicitudRepository.save(solicitud);
        return new CrearSolicitudResponse(guardada.getIdSolicitud(),
                "Solicitud registrada. La verás en tus pedidos.");
    }

    private Map<Long, String> imagenesPrincipales(List<Producto> productos) {
        if (productos.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = productos.stream().map(Producto::getIdProducto).toList();
        Map<Long, String> imagenes = new HashMap<>();
        for (ProductoImagen imagen : productoImagenRepository.buscarPrincipalesActivas(ids)) {
            imagenes.putIfAbsent(imagen.getProducto().getIdProducto(), imagen.getUrlImagen());
        }
        return imagenes;
    }

    /**
     * El cliente cancela su pedido mientras no se haya cotizado (PENDIENTE o EN_REVISION).
     * Después responde 409. Queda en la bitácora con el motivo, si lo escribió.
     */
    @Transactional
    public void cancelarPedido(Long id, String motivo) {
        Usuario usuario = exigirCliente();
        SolicitudCotizacion solicitud = solicitudRepository
                .findByIdSolicitudAndCliente_IdClienteAndStatus(id, usuario.getCliente().getIdCliente(), "A")
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
        String anterior = solicitud.getEstadoSolicitud();
        if (!ESTADOS_CANCELABLES.contains(anterior)) {
            throw new EstadoNoPermitidoException(
                    "Este pedido ya no se puede cancelar porque está " + anterior.toLowerCase(Locale.ROOT).replace('_', ' ')
                            + ". Escríbenos si necesitas cambiar algo.");
        }
        solicitud.setEstadoSolicitud(EstadoSolicitud.CANCELADA.name());
        solicitud.setUserUpdate(usuario.getNombreUsuario());
        solicitud.setProcessUpdate("CANCELAR_PORTAL");
        solicitud.setDateUpdate(LocalDateTime.now());
        solicitudRepository.save(solicitud);

        String texto = motivo == null || motivo.isBlank() ? "sin motivo" : "motivo: " + motivo.trim();
        bitacora.registrar(solicitud.getIdSolicitud(), usuario.getIdUsuario(), TipoEventoSolicitud.ESTADO,
                anterior + " → CANCELADA por el cliente (" + texto + ")");
    }

    /** Sin fila de usuario o sin id_cliente no hay portal: 403, no una lista vacía. */
    private Usuario exigirCliente() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("El acceso del portal todavía no está habilitado");
        }
        Usuario usuario = usuarioRepository.findWithClienteByNombreUsuario(auth.getName()).orElse(null);
        if (usuario == null || usuario.getCliente() == null) {
            throw new AccessDeniedException("El acceso del portal todavía no está habilitado");
        }
        return usuario;
    }

    /** Barra del cliente (D-14): solo etapas, nunca plazos ni motivos internos. */
    static String etapaCliente(String estado) {
        if (estado == null) {
            return "RECIBIDA";
        }
        return switch (estado) {
            case "PENDIENTE" -> "RECIBIDA";
            case "EN_REVISION" -> "EN_BUSQUEDA";
            case "EN_ESPERA_TERCERO" -> "EN_ESPERA_PROVEEDOR";
            default -> estado;
        };
    }

    private PedidoResponse aPedido(SolicitudCotizacion solicitud) {
        List<PedidoItemResponse> items = solicitud.getItems().stream()
                .filter(item -> "A".equals(item.getStatus()))
                .map(item -> PedidoItemResponse.builder()
                        .id(item.getIdSolicitudItem())
                        .descripcion(item.getDescripcion())
                        .cantidad(item.getCantidad())
                        .unidad(item.getUnidad())
                        .marca(item.getMarcaTexto())
                        .numeroParte(item.getNumeroParte())
                        .gtin(item.getGtin())
                        .urlFoto(item.getUrlFoto())
                        .build())
                .toList();
        return PedidoResponse.builder()
                .id(solicitud.getIdSolicitud())
                .codigo(solicitud.getCodigoSolicitud())
                .fecha(solicitud.getDateCreate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .estado(solicitud.getEstadoSolicitud())
                .etapa(etapaCliente(solicitud.getEstadoSolicitud()))
                .puedeCancelar(ESTADOS_CANCELABLES.contains(solicitud.getEstadoSolicitud()))
                .asunto(solicitud.getAsunto())
                .descripcion(solicitud.getDescripcion())
                .items(items)
                .build();
    }

    private String nombreUsuarioDisponible(String correo) {
        int arroba = correo.indexOf('@');
        String base = (arroba > 0 ? correo.substring(0, arroba) : "cliente")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9.]", "");
        if (base.isBlank()) {
            base = "cliente";
        }
        if (base.length() > MAX_NOMBRE_USUARIO) {
            base = base.substring(0, MAX_NOMBRE_USUARIO);
        }
        String candidato = base;
        for (int i = 0; i < 8 && usuarioRepository.existsByNombreUsuario(candidato); i++) {
            String sufijo = String.valueOf(1000 + ThreadLocalRandom.current().nextInt(9000));
            int corte = Math.min(base.length(), MAX_NOMBRE_USUARIO - sufijo.length());
            candidato = base.substring(0, corte) + sufijo;
        }
        if (usuarioRepository.existsByNombreUsuario(candidato)) {
            return null;
        }
        return candidato;
    }

    /**
     * ck_usuario_iniciales en PostgreSQL: solo A-Z y entre 2 y 3 letras.
     * Primero el contacto; si no alcanza, la razón social; si tampoco, las
     * letras seguidas de ambos.
     */
    private String iniciales(String contacto, String razonSocial) {
        String porPalabras = inicialesDePalabras(contacto);
        if (porPalabras.length() < 2) {
            porPalabras = inicialesDePalabras(razonSocial);
        }
        if (porPalabras.length() < 2) {
            porPalabras = letrasSeguidas((contacto == null ? "" : contacto) + " " + (razonSocial == null ? "" : razonSocial));
        }
        if (porPalabras.length() < 2) {
            return "CL";
        }
        return porPalabras.substring(0, Math.min(3, porPalabras.length()));
    }

    private String inicialesDePalabras(String texto) {
        StringBuilder sb = new StringBuilder();
        for (String parte : sinTildes(texto).split("\\s+")) {
            for (int i = 0; i < parte.length(); i++) {
                char c = Character.toUpperCase(parte.charAt(i));
                if (c >= 'A' && c <= 'Z') {
                    sb.append(c);
                    break;
                }
            }
            if (sb.length() == 3) {
                break;
            }
        }
        return sb.toString();
    }

    private String letrasSeguidas(String texto) {
        String normalizado = sinTildes(texto);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < normalizado.length() && sb.length() < 3; i++) {
            char c = Character.toUpperCase(normalizado.charAt(i));
            if (c >= 'A' && c <= 'Z') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String sinTildes(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** ck_solicitud_cotizacion_codigo: SOL-AAAA-n, y codigo_solicitud es VARCHAR(20). */
    private String nuevoCodigo() {
        int anio = LocalDate.now().getYear();
        String prefijo = "SOL-" + anio + "-";
        for (int i = 0; i < 8; i++) {
            long n = 1 + ThreadLocalRandom.current().nextLong(1_000_000_000L);
            String codigo = prefijo + n;
            if (codigo.length() <= 20 && !solicitudRepository.existsByCodigoSolicitud(codigo)) {
                return codigo;
            }
        }
        return prefijo + (System.nanoTime() % 1_000_000_000L);
    }

    private String soloDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    private String vacioComoNull(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
