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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
import com.genlogs.app.dto.CrearSolicitudRequest;
import com.genlogs.app.dto.CrearSolicitudResponse;
import com.genlogs.app.dto.PedidoItemResponse;
import com.genlogs.app.dto.PedidoResponse;
import com.genlogs.app.dto.ProductoPublicoResponse;
import com.genlogs.app.dto.RegistroClienteRequest;
import com.genlogs.app.dto.RegistroClienteResponse;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.EmpresaMinera;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.ProductoImagen;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.SolicitudCotizacion;
import com.genlogs.app.model.SolicitudItem;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.EmpresaMineraRepository;
import com.genlogs.app.repository.ProductoImagenRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.SolicitudCotizacionRepository;
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

    private final ProductoRepository productoRepository;
    private final ProductoImagenRepository productoImagenRepository;
    private final EmpresaMineraRepository empresaMineraRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final SolicitudCotizacionRepository solicitudRepository;
    private final PasswordEncoder passwordEncoder;
    private final LimitadorRegistroPortal limitadorRegistro;

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
            var distrito = empresa.getCliente().getTercero().getDistrito();
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
     * Siempre la misma respuesta. El usuario, si se crea, queda inactivo y sin token.
     * La contraseña se hashea aunque el documento no corresponda a un cliente.
     */
    public RegistroClienteResponse registrar(RegistroClienteRequest request) {
        String documento = soloDigitos(request.getDocumento());
        String correo = request.getCorreo().trim();
        limitadorRegistro.verificar(documento, correo);
        String hash = passwordEncoder.encode(request.getPassword());

        try {
            crearSiCorresponde(request, documento, correo, hash);
        } catch (DataIntegrityViolationException ex) {
            log.info("Registro de portal no aplicado por un dato ya existente");
        }
        return RegistroClienteResponse.pendiente();
    }

    private void crearSiCorresponde(RegistroClienteRequest request, String documento, String correo, String hash) {
        if (documento.length() < 8 || documento.length() > 11) {
            log.debug("Registro de portal omitido: documento fuera de rango");
            return;
        }
        Cliente cliente = clienteRepository.findByNumeroDocumento(documento).orElse(null);
        if (cliente == null || !"ACTIVO".equals(cliente.getSituacion())) {
            log.debug("Registro de portal omitido: sin cliente activo para el documento");
            return;
        }
        if (usuarioRepository.existsByCliente_IdCliente(cliente.getIdCliente())
                || usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            log.debug("Registro de portal omitido: el cliente o el correo ya tienen usuario");
            return;
        }
        Rol rol = rolRepository.findByNombreRolIgnoreCase(ROL_CLIENTE).orElse(null);
        if (rol == null) {
            log.error("Registro de portal omitido: no existe el rol CLIENTE. Ejecuta la migración.");
            return;
        }

        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        usuario.setCliente(cliente);
        String nombreUsuario = nombreUsuarioDisponible(correo);
        if (nombreUsuario == null) {
            log.info("Registro de portal omitido: no quedó un nombre de usuario libre");
            return;
        }
        usuario.setNombreUsuario(nombreUsuario);
        usuario.setNombres(request.getNombreContacto().trim());
        usuario.setCorreo(correo);
        usuario.setIniciales(iniciales(request.getNombreContacto(), request.getRazonSocial()));
        usuario.setPasswordHash(hash);
        usuario.setBloqueado(false);
        usuario.setIntentosFallidos((short) 0);
        usuario.setStatus("I");
        usuario.setUserCreate("portal");
        usuario.setProcessCreate("REGISTRO_PORTAL");
        usuario.setDateCreate(LocalDateTime.now());
        usuarioRepository.saveAndFlush(usuario);
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

    private PedidoResponse aPedido(SolicitudCotizacion solicitud) {
        List<PedidoItemResponse> items = solicitud.getItems().stream()
                .filter(item -> "A".equals(item.getStatus()))
                .map(item -> PedidoItemResponse.builder()
                        .id(item.getIdSolicitudItem())
                        .descripcion(item.getDescripcion())
                        .cantidad(item.getCantidad())
                        .unidad(item.getUnidad())
                        .build())
                .toList();
        return PedidoResponse.builder()
                .id(solicitud.getIdSolicitud())
                .codigo(solicitud.getCodigoSolicitud())
                .fecha(solicitud.getDateCreate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .estado(solicitud.getEstadoSolicitud())
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
