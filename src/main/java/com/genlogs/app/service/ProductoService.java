package com.genlogs.app.service;

import com.genlogs.app.dto.ProductoCaracteristicaRequest;
import com.genlogs.app.dto.ProductoProveedorRequest;
import com.genlogs.app.dto.ProductoRequest;
import com.genlogs.app.dto.ProductoResponse;
// ⚠️ Estas dos excepciones las crea Luana en com.genlogs.app.exception
//    (núcleo). Si aún no existen al compilar, avísale para no bloquearte.
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.CategoriaProducto;
import com.genlogs.app.model.CaracteristicaTecnica;
import com.genlogs.app.model.DocumentoProducto;
import com.genlogs.app.model.Marca;
import com.genlogs.app.model.Moneda; // [PENDIENTE] catálogo general, aún sin asignar en el equipo
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.ProductoCaracteristica;
import com.genlogs.app.model.ProductoCaracteristicaId;
import com.genlogs.app.model.ProductoImagen;
import com.genlogs.app.model.ProductoProveedor;
import com.genlogs.app.model.ProductoProveedorId;
import com.genlogs.app.model.ProductoSector;
import com.genlogs.app.model.ProductoSectorId;
import com.genlogs.app.model.Proveedor;
import com.genlogs.app.model.SectorEconomico; // la crea Mell
import com.genlogs.app.model.TipoDocumentoProducto;
import com.genlogs.app.model.UnidadMedida; // [PENDIENTE] catálogo general, aún sin asignar en el equipo
import com.genlogs.app.repository.CaracteristicaTecnicaRepository;
import com.genlogs.app.repository.CategoriaProductoRepository;
import com.genlogs.app.repository.DocumentoProductoRepository;
import com.genlogs.app.repository.MarcaRepository;
import com.genlogs.app.repository.MonedaRepository; // [PENDIENTE], igual que Moneda
import com.genlogs.app.repository.ProductoCaracteristicaRepository;
import com.genlogs.app.repository.ProductoImagenRepository;
import com.genlogs.app.repository.ProductoProveedorRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.ProductoSectorRepository;
import com.genlogs.app.repository.ProveedorRepository;
import com.genlogs.app.repository.SectorEconomicoRepository; // la crea Mell
import com.genlogs.app.repository.UnidadMedidaRepository; // [PENDIENTE], igual que UnidadMedida
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private static final String CARPETA_IMAGENES   = "genlogs/productos/imagenes";
    private static final String CARPETA_DOCUMENTOS = "genlogs/productos/documentos";

    private final ProductoRepository productoRepository;
    private final CategoriaProductoRepository categoriaProductoRepository;
    private final MarcaRepository marcaRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;
    private final CaracteristicaTecnicaRepository caracteristicaTecnicaRepository;
    private final ProductoCaracteristicaRepository productoCaracteristicaRepository;
    private final ProductoImagenRepository productoImagenRepository;
    private final DocumentoProductoRepository documentoProductoRepository;
    private final ProductoSectorRepository productoSectorRepository;
    private final SectorEconomicoRepository sectorEconomicoRepository;
    private final ProductoProveedorRepository productoProveedorRepository;
    private final ProveedorRepository proveedorRepository;
    private final MonedaRepository monedaRepository;
    private final CloudinaryService cloudinaryService;

    // ------------------------------------------------------------------
    // CRUD de Producto
    // ------------------------------------------------------------------

    @Transactional
    public ProductoResponse registrar(ProductoRequest request) {
        if (productoRepository.findByCodigoProducto(request.getCodigoProducto()).isPresent()) {
            throw new BusinessException("Ya existe un producto con el código " + request.getCodigoProducto());
        }

        Producto producto = Producto.builder()
                .categoriaProducto(buscarCategoria(request.getIdCategoriaProducto()))
                .marca(request.getIdMarca() != null ? buscarMarca(request.getIdMarca()) : null)
                .unidadMedida(buscarUnidadMedida(request.getIdUnidadMedida()))
                .codigoProducto(request.getCodigoProducto())
                .nombreProducto(request.getNombreProducto())
                .procedencia(request.getProcedencia())
                .visibleWeb(request.getVisibleWeb())
                .descripcion(request.getDescripcion())
                .build();
        producto.setUserCreate("SISTEMA");        // el interceptor de auditoría de Luana debería sobrescribir esto
        producto.setProcessCreate("ALTA_PRODUCTO");

        producto = productoRepository.save(producto);
        return toResponse(producto);
    }

    @Transactional
    public ProductoResponse actualizar(Long idProducto, ProductoRequest request) {
        Producto producto = buscarProductoActivo(idProducto);

        productoRepository.findByCodigoProducto(request.getCodigoProducto())
                .filter(otro -> !otro.getIdProducto().equals(idProducto))
                .ifPresent(otro -> {
                    throw new BusinessException("Ya existe otro producto con el código " + request.getCodigoProducto());
                });

        producto.setCategoriaProducto(buscarCategoria(request.getIdCategoriaProducto()));
        producto.setMarca(request.getIdMarca() != null ? buscarMarca(request.getIdMarca()) : null);
        producto.setUnidadMedida(buscarUnidadMedida(request.getIdUnidadMedida()));
        producto.setCodigoProducto(request.getCodigoProducto());
        producto.setNombreProducto(request.getNombreProducto());
        producto.setProcedencia(request.getProcedencia());
        producto.setVisibleWeb(request.getVisibleWeb());
        producto.setDescripcion(request.getDescripcion());
        producto.setUserUpdate("SISTEMA");
        producto.setProcessUpdate("ACTUALIZA_PRODUCTO");

        return toResponse(productoRepository.save(producto));
    }

    @Transactional(readOnly = true)
    public ProductoResponse buscarPorId(Long idProducto) {
        return toResponse(buscarProductoActivo(idProducto));
    }

    @Transactional(readOnly = true)
    public ProductoResponse buscarPorCodigo(String codigoProducto) {
        Producto producto = productoRepository.findByCodigoProducto(codigoProducto)
                .filter(p -> !"I".equals(p.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
        return toResponse(producto);
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> listarVisiblesWeb() {
        return productoRepository.findByStatusAndVisibleWeb("A", true).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> buscarPorNombre(String texto) {
        return productoRepository.buscarPorNombre(texto).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void desactivar(Long idProducto) {
        Producto producto = buscarProductoActivo(idProducto);
        producto.setStatus("I");
        producto.setUserUpdate("SISTEMA");
        producto.setProcessUpdate("BAJA_PRODUCTO");
        productoRepository.save(producto);
    }

    // ------------------------------------------------------------------
    // Ficha técnica (producto_caracteristica)
    // ------------------------------------------------------------------

    @Transactional
    public void agregarCaracteristica(Long idProducto, ProductoCaracteristicaRequest request) {
        Producto producto = buscarProductoActivo(idProducto);
        CaracteristicaTecnica caracteristica = caracteristicaTecnicaRepository
                .findById(request.getIdCaracteristica())
                .orElseThrow(() -> new ResourceNotFoundException("Característica técnica no existe"));

        ProductoCaracteristicaId id = new ProductoCaracteristicaId(idProducto, request.getIdCaracteristica());
        ProductoCaracteristica pc = productoCaracteristicaRepository.findById(id)
                .orElseGet(() -> ProductoCaracteristica.builder()
                        .id(id)
                        .producto(producto)
                        .caracteristica(caracteristica)
                        .build());
        pc.setValorCaracteristica(request.getValorCaracteristica());
        pc.setStatus("A"); // reactiva la fila si la característica se había quitado antes
        if (pc.getUserCreate() == null) {
            pc.setUserCreate("SISTEMA");
            pc.setProcessCreate("FICHA_TECNICA");
        } else {
            pc.setUserUpdate("SISTEMA");
            pc.setProcessUpdate("FICHA_TECNICA");
        }

        productoCaracteristicaRepository.save(pc);
    }

    @Transactional
    public void quitarCaracteristica(Long idProducto, Integer idCaracteristica) {
        ProductoCaracteristicaId id = new ProductoCaracteristicaId(idProducto, idCaracteristica);
        ProductoCaracteristica pc = productoCaracteristicaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El producto no tiene esa característica"));
        pc.setStatus("I");
        productoCaracteristicaRepository.save(pc);
    }

    // ------------------------------------------------------------------
    // Imágenes (Cloudinary)
    // ------------------------------------------------------------------

    @Transactional
    public void subirImagen(Long idProducto, MultipartFile archivo, boolean esPrincipal) {
        Producto producto = buscarProductoActivo(idProducto);
        String url = cloudinaryService.subirImagen(archivo, CARPETA_IMAGENES);

        if (esPrincipal) {
            productoImagenRepository
                    .findByProducto_IdProductoAndEsPrincipalTrueAndStatus(idProducto, "A")
                    .ifPresent(actual -> {
                        actual.setEsPrincipal(false);
                        productoImagenRepository.save(actual);
                    });
        }

        ProductoImagen imagen = ProductoImagen.builder()
                .producto(producto)
                .urlImagen(url)
                .esPrincipal(esPrincipal)
                .build();
        imagen.setUserCreate("SISTEMA");
        imagen.setProcessCreate("SUBIR_IMAGEN_PRODUCTO");
        productoImagenRepository.save(imagen);
    }

    @Transactional
    public void eliminarImagen(Long idProductoImagen) {
        ProductoImagen imagen = productoImagenRepository.findById(idProductoImagen)
                .orElseThrow(() -> new ResourceNotFoundException("Imagen no encontrada"));
        cloudinaryService.eliminarPorUrl(imagen.getUrlImagen(), CloudinaryService.TIPO_IMAGEN);
        imagen.setStatus("I");
        productoImagenRepository.save(imagen);
    }

    // ------------------------------------------------------------------
    // Documentos (Cloudinary)
    // ------------------------------------------------------------------

    @Transactional
    public void subirDocumento(Long idProducto, MultipartFile archivo, TipoDocumentoProducto tipo, String nombreDocumento) {
        Producto producto = buscarProductoActivo(idProducto);
        String url = cloudinaryService.subirDocumento(archivo, CARPETA_DOCUMENTOS);

        DocumentoProducto documento = DocumentoProducto.builder()
                .producto(producto)
                .tipoDocumento(tipo)
                .nombreDocumento(nombreDocumento)
                .urlDocumento(url)
                .build();
        documento.setUserCreate("SISTEMA");
        documento.setProcessCreate("SUBIR_DOCUMENTO_PRODUCTO");
        documentoProductoRepository.save(documento);
    }

    @Transactional
    public void eliminarDocumento(Long idDocumento) {
        DocumentoProducto documento = documentoProductoRepository.findById(idDocumento)
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado"));
        cloudinaryService.eliminarPorUrl(documento.getUrlDocumento(), CloudinaryService.TIPO_DOCUMENTO);
        documento.setStatus("I");
        documentoProductoRepository.save(documento);
    }

    // ------------------------------------------------------------------
    // Sectores económicos (filtros del catálogo web)
    // ------------------------------------------------------------------

    @Transactional
    public void asociarSector(Long idProducto, Integer idSectorEconomico) {
        Producto producto = buscarProductoActivo(idProducto);
        SectorEconomico sector = sectorEconomicoRepository.findById(idSectorEconomico)
                .orElseThrow(() -> new ResourceNotFoundException("Sector económico no existe"));

        ProductoSectorId id = new ProductoSectorId(idProducto, idSectorEconomico);
        ProductoSector ps = productoSectorRepository.findById(id).orElse(null);
        if (ps == null) {
            ps = ProductoSector.builder()
                    .id(id)
                    .producto(producto)
                    .sectorEconomico(sector)
                    .build();
            ps.setUserCreate("SISTEMA");
            ps.setProcessCreate("ASOCIAR_SECTOR_PRODUCTO");
        } else if ("I".equals(ps.getStatus())) {
            ps.setStatus("A"); // reactiva la asociación que se había quitado antes
            ps.setUserUpdate("SISTEMA");
            ps.setProcessUpdate("ASOCIAR_SECTOR_PRODUCTO");
        } else {
            return; // ya estaba asociado
        }
        productoSectorRepository.save(ps);
    }

    @Transactional
    public void desasociarSector(Long idProducto, Integer idSectorEconomico) {
        ProductoSectorId id = new ProductoSectorId(idProducto, idSectorEconomico);
        ProductoSector ps = productoSectorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El producto no tiene ese sector asociado"));
        ps.setStatus("I");
        productoSectorRepository.save(ps);
    }

    // ------------------------------------------------------------------
    // Proveedores del producto
    // ------------------------------------------------------------------

    @Transactional
    public void asociarProveedor(Long idProducto, ProductoProveedorRequest request) {
        Producto producto = buscarProductoActivo(idProducto);
        Proveedor proveedor = proveedorRepository.findById(request.getIdProveedor())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no existe"));

        // Misma regla que ck_producto_proveedor_moneda en la BD
        if (request.getPrecioReferencial() != null && request.getIdMoneda() == null) {
            throw new BusinessException("Debe indicar la moneda si informa un precio referencial");
        }
        Moneda moneda = request.getIdMoneda() != null
                ? monedaRepository.findById(request.getIdMoneda())
                        .orElseThrow(() -> new ResourceNotFoundException("Moneda no existe"))
                : null;

        if (Boolean.TRUE.equals(request.getProveedorPreferido())) {
            productoProveedorRepository.findByProducto_IdProductoAndStatus(idProducto, "A").forEach(actual -> {
                if (Boolean.TRUE.equals(actual.getProveedorPreferido())) {
                    actual.setProveedorPreferido(false);
                    productoProveedorRepository.save(actual);
                }
            });
        }

        ProductoProveedorId id = new ProductoProveedorId(idProducto, request.getIdProveedor());
        ProductoProveedor pp = productoProveedorRepository.findById(id)
                .orElseGet(() -> ProductoProveedor.builder().id(id).producto(producto).proveedor(proveedor).build());
        pp.setPrecioReferencial(request.getPrecioReferencial());
        pp.setMoneda(moneda);
        pp.setTiempoEntregaDias(request.getTiempoEntregaDias());
        pp.setProveedorPreferido(Boolean.TRUE.equals(request.getProveedorPreferido()));
        pp.setStatus("A");
        if (pp.getUserCreate() == null) {
            pp.setUserCreate("SISTEMA");
            pp.setProcessCreate("ASOCIAR_PROVEEDOR_PRODUCTO");
        } else {
            pp.setUserUpdate("SISTEMA");
            pp.setProcessUpdate("ASOCIAR_PROVEEDOR_PRODUCTO");
        }

        productoProveedorRepository.save(pp);
    }

    // ------------------------------------------------------------------
    // Helpers privados
    // ------------------------------------------------------------------

    private Producto buscarProductoActivo(Long idProducto) {
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
        if ("I".equals(producto.getStatus())) {
            throw new ResourceNotFoundException("Producto no encontrado");
        }
        return producto;
    }

    private CategoriaProducto buscarCategoria(Integer idCategoriaProducto) {
        return categoriaProductoRepository.findById(idCategoriaProducto)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría de producto no existe"));
    }

    private Marca buscarMarca(Integer idMarca) {
        return marcaRepository.findById(idMarca)
                .orElseThrow(() -> new ResourceNotFoundException("Marca no existe"));
    }

    private UnidadMedida buscarUnidadMedida(Integer idUnidadMedida) {
        return unidadMedidaRepository.findById(idUnidadMedida)
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de medida no existe"));
    }

    private ProductoResponse toResponse(Producto producto) {
        List<ProductoResponse.CaracteristicaValorResponse> caracteristicas =
                productoCaracteristicaRepository.findByProducto_IdProductoAndStatus(producto.getIdProducto(), "A")
                        .stream()
                        .map(pc -> ProductoResponse.CaracteristicaValorResponse.builder()
                                .nombreCaracteristica(pc.getCaracteristica().getNombreCaracteristica())
                                .unidadCaracteristica(pc.getCaracteristica().getUnidadCaracteristica())
                                .valor(pc.getValorCaracteristica())
                                .build())
                        .collect(Collectors.toList());

        String imagenPrincipal = productoImagenRepository
                .findByProducto_IdProductoAndEsPrincipalTrueAndStatus(producto.getIdProducto(), "A")
                .map(ProductoImagen::getUrlImagen)
                .orElse(null);

        return ProductoResponse.builder()
                .idProducto(producto.getIdProducto())
                .codigoProducto(producto.getCodigoProducto())
                .nombreProducto(producto.getNombreProducto())
                .procedencia(producto.getProcedencia())
                .descripcion(producto.getDescripcion())
                .visibleWeb(producto.getVisibleWeb())
                .categoria(producto.getCategoriaProducto().getNombreCategoria())
                .marca(producto.getMarca() != null ? producto.getMarca().getNombreMarca() : null)
                .unidadMedida(producto.getUnidadMedida().getCodigoUnidad())
                .imagenPrincipal(imagenPrincipal)
                .caracteristicas(caracteristicas)
                .build();
    }
}
