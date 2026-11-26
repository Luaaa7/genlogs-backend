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
import com.genlogs.app.dto.CompletarEmpresaRequest;
import com.genlogs.app.dto.CompletarEmpresaResponse;
import com.genlogs.app.dto.CuentaPortalResponse;
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
import com.genlogs.app.util.DatosTercero;

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
    /** DNI de persona natural: 8 dígitos. Con DNI se emite boleta y no se pide orden de compra. */
    private static final Pattern DNI_VALIDO = Pattern.compile("^\\d{8}$");
    private static final String SECTOR_CAPTACION = "MINERIA";
    /** Marca del usuario que pidió acceso a una empresa que ya es cliente (lo usa UsuarioService.activar). */
    public static final String PROCESO_PENDIENTE = "REGISTRO_PORTAL_PENDIENTE";

    /**
     * Acceso del portal que espera la revisión del Jefe Administrativo (BPMN 8.4): inactivo y
     * marcado como pendiente al registrarse (process_create, sin cambios después) o al completar
     * los datos de la empresa con Google (process_update). Activado o rechazado deja de serlo.
     */
    public static boolean esAccesoPendiente(Usuario usuario) {
        String status = usuario.getStatus() == null ? "" : usuario.getStatus().trim();
        if (!status.startsWith("I")) {
            return false;
        }
        return PROCESO_PENDIENTE.equals(usuario.getProcessUpdate())
                || (PROCESO_PENDIENTE.equals(usuario.getProcessCreate()) && usuario.getProcessUpdate() == null);
    }
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
     *   <li>Si el RUC no es cliente, se valida en Factiliza, se da de alta la empresa
     *       (captación de mineras, proceso 3) y el usuario nace activo con su token,
     *       así sigue sin esperar (y la landing envía su pedido guardado, D-30).</li>
     *   <li>Si el RUC ya es cliente, el usuario nuevo queda PENDIENTE (status 'I', sin
     *       token) hasta que lo active el Jefe Administrativo. El RUC es público y el
     *       portal muestra los pedidos, precios y despachos de la empresa y deja aprobar
     *       cotizaciones: nadie debe entrar a eso solo por conocer el RUC. Se avisa al
     *       Jefe Administrativo, en la campanita y por correo (BR4).
     *       Ese caso no consulta Factiliza ni consume el límite de intentos.</li>
     * </ul>
     */
    @Transactional
    public RegistroClienteResponse registrar(RegistroClienteRequest request) {
        boolean dni = esDni(request.getTipoDocumento());
        String ruc = soloDigitos(request.getDocumento());
        String correo = request.getCorreo().trim();
        String titular = nombreTitular(dni, request.getRazonSocial(), request.getNombreContacto());
        if (dni) {
            exigirDniValido(ruc);
        } else {
            if (!RUC_VALIDO.matcher(ruc).matches()) {
                throw new BusinessException(
                        "Para registrarte necesitamos tu RUC. Si eres persona natural, usa el RUC que empieza con 10.");
            }
            exigirRazonSocial(titular);
        }
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new BusinessException("Ese correo ya tiene una cuenta. Ingresa al portal con tu correo y contraseña.");
        }
        Rol rol = rolRepository.findByNombreRolIgnoreCase(ROL_CLIENTE).orElseThrow(() -> {
            log.error("Registro de portal rechazado: no existe el rol CLIENTE. Ejecuta la migración.");
            return new BusinessException("El registro no está disponible en este momento. Inténtalo más tarde.");
        });

        Cliente existente = clienteRepository.findByNumeroDocumento(ruc).orElse(null);
        boolean empresaNueva = existente == null;
        Cliente cliente = empresaNueva
                ? altaNueva(dni, ruc, correo, titular, request.getTelefono())
                : existente;
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
        usuario.setIniciales(iniciales(request.getNombreContacto(), titular));
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
     * Aviso de un acceso pendiente al Jefe Administrativo en la campanita y por correo (BR4),
     * que lo activa desde Usuarios. No se avisa a los colegas de la empresa.
     * Si el aviso falla, el registro sigue: el usuario ya quedó pendiente.
     */
    private void avisarAccesoPendiente(Cliente cliente, Usuario nuevo) {
        String empresa = cliente.getTercero().getRazonSocial();
        String ruc = cliente.getTercero().getNumeroDocumento();
        TipoDocumento tipo = cliente.getTercero().getTipoDocumento();
        String etiqueta = tipo == null || tipo.getCodigoTipo() == null ? "RUC" : tipo.getCodigoTipo().trim();
        try {
            notificacionService.avisarRol(RolSistema.JEFE_ADMINISTRATIVO, TipoAviso.ACCESO_PORTAL_PENDIENTE,
                    "Acceso al portal pendiente",
                    nuevo.getNombres() + " (" + nuevo.getCorreo() + ") pidió acceso al portal para "
                            + empresa + " (" + etiqueta + " " + ruc + "). Revísalo y actívalo en Usuarios si corresponde.",
                    "/usuarios", true);
        } catch (RuntimeException ex) {
            log.warn("No se pudo avisar el acceso pendiente del usuario {}: {}", nuevo.getIdUsuario(), ex.getMessage());
        }
    }

    /** RUC que no está en la base: el límite de intentos y, después, Factiliza. */
    private Cliente altaNueva(boolean dni, String numero, String correo, String titular, String telefono) {
        limitadorRegistro.verificar(numero, correo);
        return dni
                ? altaClienteDni(numero, titular, telefono, correo)
                : altaClienteDesdeRuc(numero, titular, telefono, correo);
    }

    /**
     * Persona natural con DNI: no hay consulta a SUNAT ni domicilio fiscal, así que el tercero se crea
     * sin distrito (tercero.id_distrito admite null) y con su nombre completo como titular. Se registra
     * con tipo de documento DNI, igual que en el Panel Interno, para que se le emita boleta.
     */
    private Cliente altaClienteDni(String dni, String nombreTitular, String telefono, String correo) {
        TipoDocumento tipoDni = tipoDocumentoRepository.findByCodigoTipo("DNI")
                .orElseThrow(() -> new BusinessException("El registro no está disponible en este momento. Inténtalo más tarde."));
        DatosTercero.validarDocumento(tipoDni, dni);

        Tercero tercero = new Tercero();
        tercero.setTipoDocumento(tipoDni);
        tercero.setNumeroDocumento(dni);
        DatosTercero.aplicarNombre(tercero, tipoDni, recortar(nombreTitular, 200), null, null, null);
        tercero.setTelefono(vacioComoNull(telefono));
        tercero.setCorreo(correo.trim());
        auditarAlta(tercero);
        tercero = terceroRepository.save(tercero);

        Cliente cliente = new Cliente();
        cliente.setTercero(tercero);
        cliente.setSectorEconomico(sectorDeCaptacion());
        cliente.setSituacion("ACTIVO");
        auditarAlta(cliente);
        log.info("Cliente persona natural nuevo desde el registro del portal");
        return clienteRepository.save(cliente);
    }

    private static boolean esDni(String tipoDocumento) {
        return tipoDocumento != null && "DNI".equalsIgnoreCase(tipoDocumento.trim());
    }

    /** Con DNI el titular es la persona de contacto; con RUC, la razón social. */
    private static String nombreTitular(boolean dni, String razonSocial, String nombreContacto) {
        String nombre = dni ? nombreContacto : razonSocial;
        return nombre == null ? "" : nombre.trim();
    }

    private static void exigirDniValido(String dni) {
        if (!DNI_VALIDO.matcher(dni).matches()) {
            throw new BusinessException("Necesitamos tu DNI de 8 dígitos.");
        }
    }

    private static void exigirRazonSocial(String razonSocial) {
        if (razonSocial.isBlank()) {
            throw new BusinessException("La razón social es obligatoria");
        }
    }

    private static final String SIN_VALIDACION_RUC = "No pudimos validar tu RUC. Inténtalo de nuevo en unos minutos.";

    /**
     * Empresa nueva: el RUC se valida en Factiliza y con esos datos se crea el
     * tercero y el cliente. Sin domicilio fiscal ubicable en el catálogo no se
     * puede crear el tercero (tercero.id_distrito), así que se pide contacto.
     */
    private Cliente altaClienteDesdeRuc(String ruc, String razonSocialEscrita, String telefono, String correo) {
        ConsultaDocumentoResponse datos;
        try {
            datos = factilizaService.consultarPorNumero(ruc);
        } catch (BusinessException ex) {
            log.info("Registro de portal rechazado: no se pudo validar el RUC {} ({})", ruc, ex.getMessage());
            throw new BusinessException("No pudimos validar tu RUC en SUNAT. Revisa el número o escríbenos para ayudarte.");
        } catch (RuntimeException ex) {
            // Un error inesperado al validar (base o Factiliza) no debe terminar en 500.
            log.warn("Registro de portal rechazado: error inesperado al validar el RUC ({})", ex.getClass().getSimpleName());
            throw new BusinessException(SIN_VALIDACION_RUC);
        }
        // Hoy FactilizaService no devuelve null (sin clave simula, y timeout o HTTP lanzan BusinessException),
        // pero si llega null no se sigue: sería un NPE y un 500.
        if (datos == null) {
            log.warn("Registro de portal rechazado: la validación del RUC no devolvió datos");
            throw new BusinessException(SIN_VALIDACION_RUC);
        }
        // Sin FACTILIZA_API_KEY, Factiliza devuelve datos simulados: no validan el RUC.
        // Si se aceptaran, un descuido en Render dejaría registrar cualquier RUC.
        if (datos.isSimulado()) {
            log.warn("Registro de portal rechazado: Factiliza en modo simulado (falta FACTILIZA_API_KEY)");
            throw new BusinessException(SIN_VALIDACION_RUC);
        }
        if (datos.getIdDistrito() == null) {
            log.info("Registro de portal rechazado: el RUC {} no tiene un distrito del catálogo", ruc);
            throw new BusinessException("No pudimos ubicar el domicilio fiscal de tu RUC. Escríbenos y te damos acceso.");
        }
        TipoDocumento tipoRuc = tipoDocumentoRepository.findByCodigoTipo("RUC")
                .orElseThrow(() -> new BusinessException("El registro no está disponible en este momento. Inténtalo más tarde."));
        Distrito distrito = distritoRepository.findById(datos.getIdDistrito())
                .orElseThrow(() -> new BusinessException("No pudimos ubicar el domicilio fiscal de tu RUC. Escríbenos y te damos acceso."));

        // Si Factiliza no trae razón social, se usa la que escribió el visitante.
        String razonSocial = datos.getRazonSocial() == null || datos.getRazonSocial().isBlank()
                ? razonSocialEscrita.trim()
                : datos.getRazonSocial().trim();

        Tercero tercero = new Tercero();
        tercero.setTipoDocumento(tipoRuc);
        tercero.setNumeroDocumento(ruc);
        tercero.setRazonSocial(recortar(razonSocial, 200));
        tercero.setDistrito(distrito);
        tercero.setDireccion(recortar(datos.getDireccion(), 255));
        tercero.setTelefono(vacioComoNull(telefono));
        tercero.setCorreo(correo.trim());
        auditarAlta(tercero);
        tercero = terceroRepository.save(tercero);

        Cliente cliente = new Cliente();
        cliente.setTercero(tercero);
        cliente.setSectorEconomico(sectorDeCaptacion());
        cliente.setSituacion("ACTIVO");
        auditarAlta(cliente);
        log.info("Cliente nuevo desde el registro del portal: RUC {}", ruc);
        return clienteRepository.save(cliente);
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

    /**
     * Cuenta que entró con Google y todavía no tiene empresa: antes de su primera
     * cotización da su RUC. Se aplican las mismas reglas que en el registro (D-10):
     * <ul>
     *   <li>Empresa nueva: límite de intentos, validación en Factiliza y alta del cliente;
     *       la cuenta sigue activa y ya puede pedir cotizaciones.</li>
     *   <li>Empresa que ya es cliente: la cuenta queda pendiente (status 'I') hasta que la
     *       active el Jefe Administrativo, porque el RUC es público. Se le avisa (BR4).</li>
     * </ul>
     */
    @Transactional
    public CompletarEmpresaResponse completarEmpresa(CompletarEmpresaRequest request) {
        Usuario usuario = exigirUsuarioPortal();
        if (usuario.getCliente() != null) {
            throw new EstadoNoPermitidoException("Tu cuenta ya tiene los datos de tu empresa.");
        }
        boolean dni = esDni(request.getTipoDocumento());
        String ruc = soloDigitos(request.getRuc());
        String titular = nombreTitular(dni, request.getRazonSocial(), request.getNombreContacto());
        if (dni) {
            exigirDniValido(ruc);
        } else {
            if (!RUC_VALIDO.matcher(ruc).matches()) {
                throw new BusinessException(
                        "Necesitamos el RUC de tu empresa (11 dígitos, empieza con 10 o 20). Si eres persona natural, usa tu DNI o tu RUC 10.");
            }
            exigirRazonSocial(titular);
        }
        Cliente existente = clienteRepository.findByNumeroDocumento(ruc).orElse(null);
        boolean empresaNueva = existente == null;
        Cliente cliente = empresaNueva
                ? altaNueva(dni, ruc, usuario.getCorreo(), titular, request.getTelefono())
                : existente;
        if (!"ACTIVO".equals(cliente.getSituacion())) {
            throw new BusinessException("Tu empresa no tiene el acceso al portal habilitado. Escríbenos para ayudarte.");
        }

        usuario.setCliente(cliente);
        usuario.setNombres(recortar(request.getNombreContacto(), 150));
        usuario.setUserUpdate(usuario.getNombreUsuario());
        usuario.setDateUpdate(LocalDateTime.now());
        if (empresaNueva) {
            usuario.setProcessUpdate("COMPLETAR_EMPRESA");
            usuarioRepository.save(usuario);
            return new CompletarEmpresaResponse(false, "Listo, ya puedes pedir tus cotizaciones.");
        }
        usuario.setStatus("I");
        usuario.setProcessUpdate(PROCESO_PENDIENTE);
        usuarioRepository.save(usuario);
        avisarAccesoPendiente(cliente, usuario);
        return new CompletarEmpresaResponse(true,
                "Tu empresa ya tiene cuenta en el portal. Tu acceso quedó pendiente: "
                        + "GENLOGS lo revisará y te avisaremos por correo cuando esté habilitado.");
    }

    /** Datos de la cuenta para el portal: si ya tiene empresa (perfil completo). */
    @Transactional(readOnly = true)
    public CuentaPortalResponse cuenta() {
        Usuario usuario = exigirUsuarioPortal();
        Cliente cliente = usuario.getCliente();
        return new CuentaPortalResponse(usuario.getNombres(), usuario.getCorreo(), cliente != null,
                cliente == null ? null : cliente.getTercero().getRazonSocial());
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

    /** Usuario del portal autenticado, tenga o no empresa (cuentas que entraron con Google). */
    private Usuario exigirUsuarioPortal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("El acceso del portal todavía no está habilitado");
        }
        return usuarioRepository.findWithClienteByNombreUsuario(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("El acceso del portal todavía no está habilitado"));
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

    /** También lo usa el ingreso con Google (IngresoGoogleService). */
    String nombreUsuarioDisponible(String correo) {
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
    String iniciales(String contacto, String razonSocial) {
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