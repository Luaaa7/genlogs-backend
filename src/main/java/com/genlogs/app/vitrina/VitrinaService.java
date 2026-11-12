package com.genlogs.app.vitrina;

import com.genlogs.app.enums.EstadoPublicacion;
import com.genlogs.app.enums.OrigenRepuesto;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.CategoriaProducto;
import com.genlogs.app.model.Marca;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.ProductoImagen;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CategoriaProductoRepository;
import com.genlogs.app.repository.MarcaRepository;
import com.genlogs.app.repository.ProductoImagenRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.UnidadMedidaRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.NotificacionService;
import com.genlogs.app.service.ProductoService;
import com.genlogs.app.sourcing.EstadoSourcing;
import com.genlogs.app.sourcing.HistorialSourcing;
import com.genlogs.app.sourcing.HistorialSourcingRepository;
import com.genlogs.app.vitrina.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Subproceso 8.3 · Vitrina de repuestos (BPMN 10, RF-79, RF-82, D-17, D-22, D-26).
 *
 * referencia ÚTIL → convertir (BORRADOR) → foto propia verificada → publicar.
 * El público solo ve PUBLICADO: la landing sin precio y el portal con precio.
 * Ninguna respuesta pública lleva fuente, enlace ni precio original (D-13).
 */
@Service
@RequiredArgsConstructor
public class VitrinaService {

    static final String PLAZO_NACIONAL = "24 a 48 horas";
    static final String PLAZO_IMPORTADO = "5 a 7 días hábiles";
    private static final int MAXIMO_PUBLICO = 300;

    private final ProductoRepository productoRepository;
    private final ProductoImagenRepository productoImagenRepository;
    private final HistorialSourcingRepository historialRepository;
    private final CategoriaProductoRepository categoriaRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;
    private final MarcaRepository marcaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoService productoService;
    private final MargenRepuesto margenRepuesto;
    private final NotificacionService notificacionService;

    // ------------------------------------------------------------------ interno

    /** Convierte una referencia ÚTIL en producto BORRADOR con su precio referencial. */
    @Transactional
    public ProductoVitrinaResponse convertir(ConvertirProductoRequest req) {
        HistorialSourcing ref = historialRepository.findById(req.idSourcing())
                .orElseThrow(() -> new ResourceNotFoundException("No existe la referencia " + req.idSourcing()));
        if (ref.getEstado() == EstadoSourcing.CONVERTIDO) {
            throw new BusinessException("Esta referencia ya se convirtió en el producto " + ref.getIdProducto());
        }
        if (ref.getEstado() != EstadoSourcing.UTIL) {
            throw new BusinessException("Marca la referencia como ÚTIL antes de convertirla");
        }

        BigDecimal costo = req.costoPuestoLima() != null ? req.costoPuestoLima() : ref.getCostoPuestoLima();
        if (costo == null) {
            throw new BusinessException("Falta el costo puesto en Lima: captúralo con «Pegar URL» o escríbelo al convertir");
        }
        MargenRepuesto.Rangos rangos = margenRepuesto.rangos();
        BigDecimal margen = req.margen() != null ? req.margen() : rangos.sugerido();
        BigDecimal precio = MargenRepuesto.precio(costo, margen);
        MargenRepuesto.Evaluacion evaluacion = rangos.evaluar(margen);

        CategoriaProducto categoria = categoriaRepository.findById(req.idCategoriaProducto())
                .orElseThrow(() -> new BusinessException("La categoría elegida no existe"));
        UnidadMedida unidad = unidadMedidaRepository.findById(req.idUnidadMedida())
                .orElseThrow(() -> new BusinessException("La unidad de medida elegida no existe"));
        Marca marca = req.idMarca() == null ? null : marcaRepository.findById(req.idMarca())
                .orElseThrow(() -> new BusinessException("La marca elegida no existe"));

        OrigenRepuesto origen = req.origen() != null ? req.origen() : origenDe(ref.getOrigen());
        String numeroParte = textoONulo(req.numeroParte()) != null ? req.numeroParte().trim() : ref.getNumeroParte();

        Producto producto = Producto.builder()
                .categoriaProducto(categoria)
                .unidadMedida(unidad)
                .marca(marca)
                .codigoProducto(productoService.generarSiguienteCodigo())
                .nombreProducto(req.nombre().trim())
                .descripcion(textoONulo(req.descripcion()))
                .procedencia(origen == OrigenRepuesto.IMPORTADO ? "Importado" : "Nacional")
                .visibleWeb(false)
                .numeroParte(numeroParte)
                .tipoRepuesto(req.tipoRepuesto())
                .origen(origen)
                .estadoPublicacion(EstadoPublicacion.BORRADOR)
                .precioReferencial(precio)
                .fechaUltimaVerificacion(LocalDateTime.now())
                .build();
        producto.setUserCreate(nombreUsuario());
        producto.setProcessCreate("VITRINA_CONVERTIR");
        producto = productoRepository.save(producto);

        ref.setEstado(EstadoSourcing.CONVERTIDO);
        ref.setIdProducto(producto.getIdProducto());
        historialRepository.save(ref);

        if (evaluacion == MargenRepuesto.Evaluacion.FUERA_DE_RANGO) {
            // D-26: queda en BORRADOR y solo el Jefe Administrativo lo publica.
            notificacionService.avisarRol(RolSistema.JEFE_ADMINISTRATIVO, TipoAviso.MARGEN_FUERA_RANGO,
                    "Repuesto con margen fuera de rango",
                    producto.getNombreProducto() + " (" + producto.getCodigoProducto() + ") tiene un margen de "
                            + margen.stripTrailingZeros().toPlainString() + " %. Queda en borrador hasta que lo revises y publiques.",
                    "/vitrina/productos/" + producto.getIdProducto(), false);
        }
        return aVistaInterna(producto, List.of(), costo, rangos);
    }

    /** Registra una foto propia ya subida; quien la registra la da por verificada (RF-79). */
    @Transactional
    public ProductoVitrinaResponse agregarImagen(Long idProducto, ImagenProductoRequest req) {
        Producto producto = productoActivo(idProducto);
        List<ProductoImagen> actuales = productoImagenRepository.findByProducto_IdProductoAndStatus(idProducto, "A");
        boolean principal = req.principal() || actuales.stream().noneMatch(i -> Boolean.TRUE.equals(i.getEsPrincipal()));
        if (principal) {
            for (ProductoImagen otra : actuales) {
                if (Boolean.TRUE.equals(otra.getEsPrincipal())) {
                    otra.setEsPrincipal(false);
                    productoImagenRepository.saveAndFlush(otra);
                }
            }
        }
        ProductoImagen imagen = ProductoImagen.builder()
                .producto(producto)
                .urlImagen(req.urlImagen().trim())
                .esPrincipal(principal)
                .fuenteImagen(req.fuente())
                .verificadaPor(usuarioActual())
                .verificadaEn(LocalDateTime.now())
                .build();
        imagen.setUserCreate(nombreUsuario());
        imagen.setProcessCreate("VITRINA_IMAGEN");
        productoImagenRepository.save(imagen);
        return detalle(idProducto);
    }

    /**
     * Publica el producto. Exige categoría, unidad, precio y al menos una foto
     * verificada. Si el margen está fuera de rango, solo el Jefe Adm. publica (D-26).
     */
    @Transactional
    public ProductoVitrinaResponse publicar(Long idProducto) {
        Producto producto = productoActivo(idProducto);
        if (producto.getEstadoPublicacion() == EstadoPublicacion.RETIRADO) {
            throw new BusinessException("El producto fue retirado de la vitrina y no se puede volver a publicar");
        }
        if (producto.getCategoriaProducto() == null || producto.getUnidadMedida() == null) {
            throw new BusinessException("Completa la categoría y la unidad de medida antes de publicar");
        }
        if (producto.getPrecioReferencial() == null) {
            throw new BusinessException("Falta el precio referencial");
        }
        List<ProductoImagen> imagenes = productoImagenRepository.findByProducto_IdProductoAndStatus(idProducto, "A");
        if (imagenes.stream().noneMatch(ProductoImagen::estaVerificada)) {
            throw new BusinessException("Sube una foto propia verificada antes de publicar");
        }
        BigDecimal costo = costoDe(idProducto);
        MargenRepuesto.Rangos rangos = margenRepuesto.rangos();
        BigDecimal margen = MargenRepuesto.margenDe(costo, producto.getPrecioReferencial());
        if (margen != null && rangos.evaluar(margen) == MargenRepuesto.Evaluacion.FUERA_DE_RANGO
                && !tieneRol(RolSistema.JEFE_ADMINISTRATIVO)) {
            throw new BusinessException("El margen está fuera de rango: solo el Jefe Administrativo puede publicarlo");
        }

        producto.setEstadoPublicacion(EstadoPublicacion.PUBLICADO);
        producto.setVisibleWeb(true);
        producto.setFechaUltimaVerificacion(LocalDateTime.now());
        producto.setFallasCotizacion((short) 0);
        producto.setUserUpdate(nombreUsuario());
        producto.setProcessUpdate("VITRINA_PUBLICAR");
        productoRepository.save(producto);
        return aVistaInterna(producto, imagenes, costo, rangos);
    }

    /** Saca el producto de la vitrina sin borrarlo (D-33). RETIRADO ya no vuelve a publicarse. */
    @Transactional
    public ProductoVitrinaResponse cambiarEstado(Long idProducto, EstadoPublicacion nuevo) {
        if (nuevo != EstadoPublicacion.OCULTO && nuevo != EstadoPublicacion.RETIRADO) {
            throw new BusinessException("Aquí solo se puede ocultar o retirar; para publicar usa «Publicar»");
        }
        Producto producto = productoActivo(idProducto);
        producto.setEstadoPublicacion(nuevo);
        producto.setVisibleWeb(false);
        producto.setUserUpdate(nombreUsuario());
        producto.setProcessUpdate("VITRINA_" + nuevo.name());
        productoRepository.save(producto);
        return detalle(idProducto);
    }

    @Transactional(readOnly = true)
    public ProductoVitrinaResponse detalle(Long idProducto) {
        Producto producto = productoActivo(idProducto);
        return aVistaInterna(producto,
                productoImagenRepository.findByProducto_IdProductoAndStatus(idProducto, "A"),
                costoDe(idProducto), margenRepuesto.rangos());
    }

    /** Lista interna por estado (sin estado: borradores, publicados y ocultos). */
    @Transactional(readOnly = true)
    public List<ProductoVitrinaResponse> listarInternos(EstadoPublicacion estado) {
        Specification<Producto> spec = (root, q, cb) -> cb.equal(root.get("status"), "A");
        spec = spec.and(estado != null
                ? (root, q, cb) -> cb.equal(root.get("estadoPublicacion"), estado)
                : (root, q, cb) -> root.get("estadoPublicacion").in(
                        EstadoPublicacion.BORRADOR, EstadoPublicacion.PUBLICADO, EstadoPublicacion.OCULTO));
        List<Producto> productos = productoRepository.findAll(spec, Sort.by(Sort.Order.desc("idProducto")));
        if (productos.isEmpty()) return List.of();
        Map<Long, List<ProductoImagen>> imagenes = imagenesPorProducto(productos);
        MargenRepuesto.Rangos rangos = margenRepuesto.rangos();
        return productos.stream()
                .map(p -> aVistaInterna(p, imagenes.getOrDefault(p.getIdProducto(), List.of()), costoDe(p.getIdProducto()), rangos))
                .toList();
    }

    // ------------------------------------------------------------------ público y portal

    /** Landing: solo PUBLICADO, sin precio. */
    @Transactional(readOnly = true)
    public List<RepuestoPublicoResponse> catalogoPublico(String texto, Integer idCategoria, String marca) {
        List<Producto> productos = publicados(texto, idCategoria, marca);
        Map<Long, String> fotos = fotosPublicas(productos);
        return productos.stream().map(p -> new RepuestoPublicoResponse(
                p.getIdProducto(), p.getNombreProducto(), p.getDescripcion(), nombreMarca(p), p.getNumeroParte(),
                p.getCategoriaProducto().getIdCategoriaProducto(), p.getCategoriaProducto().getNombreCategoria(),
                p.getTipoRepuesto(), plazo(p.getOrigen()), fotos.get(p.getIdProducto()))).toList();
    }

    @Transactional(readOnly = true)
    public RepuestoPublicoResponse repuestoPublico(Long idProducto) {
        Producto p = publicado(idProducto);
        return new RepuestoPublicoResponse(p.getIdProducto(), p.getNombreProducto(), p.getDescripcion(), nombreMarca(p),
                p.getNumeroParte(), p.getCategoriaProducto().getIdCategoriaProducto(),
                p.getCategoriaProducto().getNombreCategoria(), p.getTipoRepuesto(), plazo(p.getOrigen()),
                fotosPublicas(List.of(p)).get(p.getIdProducto()));
    }

    /** Categorías con cuántos repuestos publicados tienen (landing). */
    @Transactional(readOnly = true)
    public List<CategoriaConteoResponse> categoriasPublicas() {
        return publicados(null, null, null).stream()
                .collect(Collectors.groupingBy(p -> p.getCategoriaProducto().getIdCategoriaProducto(),
                        LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(lista -> new CategoriaConteoResponse(
                        lista.get(0).getCategoriaProducto().getIdCategoriaProducto(),
                        lista.get(0).getCategoriaProducto().getNombreCategoria(), lista.size()))
                .sorted(Comparator.comparing(CategoriaConteoResponse::nombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Portal del cliente: solo PUBLICADO, con precio referencial en soles. */
    @Transactional(readOnly = true)
    public List<RepuestoPortalResponse> repuestosPortal(String texto, Integer idCategoria, String marca) {
        List<Producto> productos = publicados(texto, idCategoria, marca);
        Map<Long, String> fotos = fotosPublicas(productos);
        return productos.stream().map(p -> aPortal(p, fotos.get(p.getIdProducto()))).toList();
    }

    @Transactional(readOnly = true)
    public RepuestoPortalResponse repuestoPortal(Long idProducto) {
        Producto p = publicado(idProducto);
        return aPortal(p, fotosPublicas(List.of(p)).get(p.getIdProducto()));
    }

    // ------------------------------------------------------------------ apoyo

    private List<Producto> publicados(String texto, Integer idCategoria, String marca) {
        Specification<Producto> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("status"), "A"),
                cb.equal(root.get("estadoPublicacion"), EstadoPublicacion.PUBLICADO));
        String t = textoONulo(texto);
        if (t != null) {
            String patron = "%" + t.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nombreProducto")), patron),
                    cb.like(cb.lower(root.get("numeroParte")), patron)));
        }
        if (idCategoria != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("categoriaProducto").get("idCategoriaProducto"), idCategoria));
        }
        String m = textoONulo(marca);
        if (m != null) {
            spec = spec.and((root, q, cb) -> cb.equal(cb.lower(root.join("marca").get("nombreMarca")), m.toLowerCase(Locale.ROOT)));
        }
        List<Producto> lista = productoRepository.findAll(spec, Sort.by("nombreProducto"));
        return lista.size() > MAXIMO_PUBLICO ? lista.subList(0, MAXIMO_PUBLICO) : lista;
    }

    private Producto publicado(Long idProducto) {
        return productoRepository.findById(idProducto)
                .filter(p -> "A".equals(p.getStatus()) && p.getEstadoPublicacion() == EstadoPublicacion.PUBLICADO)
                .orElseThrow(() -> new ResourceNotFoundException("El repuesto no está disponible"));
    }

    private Producto productoActivo(Long idProducto) {
        return productoRepository.findById(idProducto)
                .filter(p -> "A".equals(p.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("No existe el producto " + idProducto));
    }

    private RepuestoPortalResponse aPortal(Producto p, String foto) {
        return new RepuestoPortalResponse(p.getIdProducto(), p.getNombreProducto(), p.getDescripcion(), nombreMarca(p),
                p.getNumeroParte(), p.getCategoriaProducto().getIdCategoriaProducto(),
                p.getCategoriaProducto().getNombreCategoria(),
                p.getUnidadMedida() != null ? p.getUnidadMedida().getNombreUnidad() : null,
                p.getTipoRepuesto(), plazo(p.getOrigen()), foto, p.getPrecioReferencial(), "PEN");
    }

    private ProductoVitrinaResponse aVistaInterna(Producto p, List<ProductoImagen> imagenes, BigDecimal costo,
                                                  MargenRepuesto.Rangos rangos) {
        BigDecimal margen = MargenRepuesto.margenDe(costo, p.getPrecioReferencial());
        MargenRepuesto.Evaluacion evaluacion = margen != null ? rangos.evaluar(margen) : null;
        List<ProductoVitrinaResponse.Imagen> vistas = imagenes.stream()
                .map(i -> new ProductoVitrinaResponse.Imagen(i.getIdProductoImagen(), i.getUrlImagen(), i.getFuenteImagen(),
                        Boolean.TRUE.equals(i.getEsPrincipal()), i.estaVerificada()))
                .toList();
        return new ProductoVitrinaResponse(
                p.getIdProducto(), p.getCodigoProducto(), p.getNombreProducto(), p.getDescripcion(),
                p.getCategoriaProducto() != null ? p.getCategoriaProducto().getIdCategoriaProducto() : null,
                p.getCategoriaProducto() != null ? p.getCategoriaProducto().getNombreCategoria() : null,
                p.getUnidadMedida() != null ? p.getUnidadMedida().getIdUnidadMedida() : null,
                p.getUnidadMedida() != null ? p.getUnidadMedida().getNombreUnidad() : null,
                nombreMarca(p), p.getNumeroParte(), p.getTipoRepuesto(), p.getOrigen(), p.getEstadoPublicacion(),
                costo, margen, p.getPrecioReferencial(), evaluacion,
                evaluacion == MargenRepuesto.Evaluacion.FUERA_DE_RANGO,
                vistas.stream().anyMatch(ProductoVitrinaResponse.Imagen::verificada),
                p.getFechaUltimaVerificacion(), vistas);
    }

    /** Solo fotos propias verificadas; la principal primero. Sin foto → null (ícono de la categoría). */
    private Map<Long, String> fotosPublicas(List<Producto> productos) {
        Map<Long, String> fotos = new HashMap<>();
        imagenesPorProducto(productos).forEach((id, lista) -> lista.stream()
                .filter(ProductoImagen::estaVerificada)
                .sorted(Comparator.comparing((ProductoImagen i) -> !Boolean.TRUE.equals(i.getEsPrincipal())))
                .findFirst()
                .ifPresent(i -> fotos.put(id, i.getUrlImagen())));
        return fotos;
    }

    private Map<Long, List<ProductoImagen>> imagenesPorProducto(List<Producto> productos) {
        if (productos.isEmpty()) return Map.of();
        List<Long> ids = productos.stream().map(Producto::getIdProducto).toList();
        return productoImagenRepository.findByProducto_IdProductoInAndStatus(ids, "A").stream()
                .collect(Collectors.groupingBy(i -> i.getProducto().getIdProducto()));
    }

    private BigDecimal costoDe(Long idProducto) {
        return historialRepository.findFirstByIdProductoOrderByCreadoEnDesc(idProducto)
                .map(HistorialSourcing::getCostoPuestoLima)
                .orElse(null);
    }

    static String plazo(OrigenRepuesto origen) {
        return origen == OrigenRepuesto.IMPORTADO ? PLAZO_IMPORTADO : PLAZO_NACIONAL;
    }

    private static OrigenRepuesto origenDe(String texto) {
        if (texto == null) return OrigenRepuesto.NACIONAL;
        try {
            return OrigenRepuesto.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return OrigenRepuesto.NACIONAL;
        }
    }

    private static String nombreMarca(Producto p) {
        return p.getMarca() != null ? p.getMarca().getNombreMarca() : null;
    }

    private static String textoONulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean tieneRol(RolSistema rol) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + rol.name()).equals(a.getAuthority()));
    }

    private static String nombreUsuario() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "sistema";
    }

    private Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return usuarioRepository.findByNombreUsuario(auth.getName()).orElse(null);
    }
}
