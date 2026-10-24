package com.genlogs.app.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.PageResponseDto;
import com.genlogs.app.dto.ProductoResponse;
import com.genlogs.app.model.Auditable;
import com.genlogs.app.model.CategoriaProducto;
import com.genlogs.app.model.Marca;
import com.genlogs.app.model.Pais;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.service.ProductoService;

/**
 * Integración real del catálogo de repuestos contra H2 (sin mocks): verifica
 * el orden global ascendente por código, los metadatos de paginación y los
 * filtros. Es la prueba que detecta el defecto original (el backend ordenaba
 * por nombre, no por código).
 *
 * Los datos se insertan deliberadamente en orden inverso al del código y con
 * nombres en orden alfabético inverso: si la consulta no ordenara por
 * codigo_producto, los resultados saldrían en otro orden.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductoIntegracionTest {

    private static final int TAMANIO_PAGINA = 12;
    private static final int TOTAL_PRODUCTOS = 30;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CategoriaProductoRepository categoriaProductoRepository;

    @Autowired
    private MarcaRepository marcaRepository;

    @Autowired
    private UnidadMedidaRepository unidadMedidaRepository;

    @Autowired
    private PaisRepository paisRepository;

    private CategoriaProducto categoriaGeneral;
    private CategoriaProducto categoriaMinera;
    private Marca marcaA;
    private Marca marcaB;
    private UnidadMedida unidad;

    @BeforeEach
    void seed() {
        Pais pais = paisRepository.save(auditar(new Pais(), e -> {
            e.setCodigoIso("PER");
            e.setNombrePais("Perú");
        }));
        marcaA = marcaRepository.save(auditar(new Marca(), e -> {
            e.setPais(pais);
            e.setNombreMarca("MARCA-A");
        }));
        marcaB = marcaRepository.save(auditar(new Marca(), e -> {
            e.setPais(pais);
            e.setNombreMarca("MARCA-B");
        }));
        categoriaGeneral = categoriaProductoRepository.save(auditar(new CategoriaProducto(), e -> {
            e.setNombreCategoria("General");
            e.setSlugWeb("general-test");
            e.setNivel((short) 1);
        }));
        categoriaMinera = categoriaProductoRepository.save(auditar(new CategoriaProducto(), e -> {
            e.setNombreCategoria("Minería");
            e.setSlugWeb("mineria-test");
            e.setNivel((short) 1);
        }));
        unidad = unidadMedidaRepository.save(auditar(new UnidadMedida(), e -> {
            e.setCodigoUnidad("UND");
            e.setNombreUnidad("Unidad");
        }));

        // 30 productos GLB-000001..GLB-000030 insertados del último al primero
        // y con el nombre en orden alfabético INVERSO al código.
        for (int i = TOTAL_PRODUCTOS; i >= 1; i--) {
            final int numero = i;
            String codigo = String.format("GLB-%06d", numero);
            String nombre = String.format("Repuesto %02d", TOTAL_PRODUCTOS + 1 - numero);
            productoRepository.save(auditar(new Producto(), e -> {
                e.setCodigoProducto(codigo);
                e.setNombreProducto(nombre);
                e.setCategoriaProducto(numero <= 20 ? categoriaMinera : categoriaGeneral);
                e.setMarca(numero % 2 == 0 ? marcaA : marcaB);
                e.setUnidadMedida(unidad);
                e.setVisibleWeb(true);
            }));
        }

        // Producto inactivo: NO debe aparecer en el listado.
        Producto inactivo = auditar(new Producto(), e -> {
            e.setCodigoProducto("GLB-000031");
            e.setNombreProducto("Repuesto 99");
            e.setCategoriaProducto(categoriaGeneral);
            e.setMarca(marcaA);
            e.setUnidadMedida(unidad);
            e.setVisibleWeb(true);
        });
        inactivo.setStatus("I");
        productoRepository.save(inactivo);

        // Otro prefijo: en orden lexicográfico va después de todos los GLB.
        productoRepository.save(auditar(new Producto(), e -> {
            e.setCodigoProducto("OTR-000001");
            e.setNombreProducto("Otro producto");
            e.setCategoriaProducto(categoriaGeneral);
            e.setUnidadMedida(unidad);
            e.setVisibleWeb(true);
        }));
    }

    @Test
    void listar_ordenaAscendentePorCodigoYNoPorNombre() {
        List<String> codigos = codigosDePagina(0);

        assertEquals(List.of("GLB-000001", "GLB-000002", "GLB-000003", "GLB-000004", "GLB-000005",
                "GLB-000006", "GLB-000007", "GLB-000008", "GLB-000009", "GLB-000010", "GLB-000011",
                "GLB-000012"), codigos);
    }

    @Test
    void listar_devuelveElTotalRealSinFiltros() {
        PageResponseDto<ProductoResponse> pagina = productoService.listarPaginado(
                null, null, null, null, 0, TAMANIO_PAGINA);

        // 30 activos + 1 con otro prefijo. El inactivo (GLB-000031) no cuenta.
        assertEquals(31, pagina.getTotalElements());
        assertEquals(3, pagina.getTotalPages());
        assertEquals(0, pagina.getPage());
        assertEquals(TAMANIO_PAGINA, pagina.getSize());
        assertEquals(TAMANIO_PAGINA, pagina.getContent().size());
    }

    @Test
    void listar_primeraPaginaTraeLosPrimeros12Codigos() {
        assertEquals(12, codigosDePagina(0).size());
        assertEquals("GLB-000001", codigosDePagina(0).get(0));
        assertEquals("GLB-000012", codigosDePagina(0).get(11));
    }

    @Test
    void listar_segundaPaginaTraeLosSiguientes12Codigos() {
        List<String> codigos = codigosDePagina(1);

        assertEquals(12, codigos.size());
        assertEquals("GLB-000013", codigos.get(0));
        assertEquals("GLB-000024", codigos.get(11));
    }

    @Test
    void listar_alAvanzarDePaginaNoHayDuplicadosNiSaltos() {
        List<String> todas = new ArrayList<>();
        for (int pagina = 0; pagina < 3; pagina++) {
            todas.addAll(codigosDePagina(pagina));
        }

        assertEquals(31, todas.size());
        assertEquals(31, todas.stream().distinct().count(), "Hay productos repetidos entre páginas");

        List<String> ordenadas = todas.stream().sorted().toList();
        assertEquals(ordenadas, todas, "El orden global no es ascendente por código");
    }

    @Test
    void listar_ultimaPaginaContinuaLaSecuencia() {
        List<String> ultima = codigosDePagina(2);

        assertEquals(7, ultima.size());
        assertEquals("GLB-000025", ultima.get(0));
        assertEquals("GLB-000030", ultima.get(5));
        assertEquals("OTR-000001", ultima.get(6));
    }

    @Test
    void listar_paginaFueraDeRango_devuelveVaciaPeroConLosMetadatos() {
        PageResponseDto<ProductoResponse> pagina = productoService.listarPaginado(
                null, null, null, null, 99, TAMANIO_PAGINA);

        assertTrue(pagina.getContent().isEmpty());
        assertEquals(31, pagina.getTotalElements());
        assertEquals(3, pagina.getTotalPages());
    }

    @Test
    void listar_sinResultados_devuelveVacioConTotalCero() {
        PageResponseDto<ProductoResponse> pagina = productoService.listarPaginado(
                "no-existe-este-producto", null, null, null, 0, TAMANIO_PAGINA);

        assertTrue(pagina.getContent().isEmpty());
        assertEquals(0, pagina.getTotalElements());
        assertEquals(0, pagina.getTotalPages());
    }

    @Test
    void listar_buscaPorCodigo() {
        PageResponseDto<ProductoResponse> pagina = productoService.listarPaginado(
                null, "000013", null, null, 0, TAMANIO_PAGINA);

        assertEquals(1, pagina.getTotalElements());
        assertEquals("GLB-000013", pagina.getContent().get(0).getCodigoProducto());
    }

    @Test
    void listar_buscaPorNombre() {
        PageResponseDto<ProductoResponse> pagina = productoService.listarPaginado(
                "Repuesto 05", null, null, null, 0, TAMANIO_PAGINA);

        assertEquals(1, pagina.getTotalElements());
        // El nombre "Repuesto 05" corresponde al código GLB-000026.
        assertEquals("GLB-000026", pagina.getContent().get(0).getCodigoProducto());
    }

    @Test
    void listar_filtraPorCategoria() {
        PageResponseDto<ProductoResponse> pagina = productoService.listarPaginado(
                null, null, categoriaMinera.getIdCategoriaProducto(), null, 0, TAMANIO_PAGINA);

        assertEquals(20, pagina.getTotalElements());
        assertEquals(2, pagina.getTotalPages());
        assertTrue(pagina.getContent().stream()
                .allMatch(p -> p.getIdCategoriaProducto().equals(categoriaMinera.getIdCategoriaProducto())));
    }

    @Test
    void listar_filtraPorMarca() {
        PageResponseDto<ProductoResponse> pagina = productoService.listarPaginado(
                null, null, null, marcaA.getIdMarca(), 0, TAMANIO_PAGINA);

        // La mitad de los GLB (los de índice par) son de MARCA-A.
        assertEquals(15, pagina.getTotalElements());
        assertTrue(pagina.getContent().stream()
                .allMatch(p -> p.getIdMarca().equals(marcaA.getIdMarca())));
    }

    @Test
    void listar_excluyeLosProductosInactivos() {
        List<String> codigos = codigosDePagina(0);
        codigos.addAll(codigosDePagina(1));
        codigos.addAll(codigosDePagina(2));

        assertFalse(codigos.contains("GLB-000031"), "El producto inactivo no debe listarse");
    }

    @Test
    void listar_limitaElTamanioDePaginaA100Registros() {
        PageResponseDto<ProductoResponse> pagina = productoService.listarPaginado(
                null, null, null, null, 0, 500);

        assertEquals(100, pagina.getSize());
        assertEquals(31, pagina.getTotalElements());
        assertEquals(1, pagina.getTotalPages());
    }

    @Test
    void listar_conPaginaNegativaLaCorrige() {
        PageResponseDto<ProductoResponse> pagina = productoService.listarPaginado(
                null, null, null, null, -3, TAMANIO_PAGINA);

        assertEquals(0, pagina.getPage());
        assertEquals("GLB-000001", pagina.getContent().get(0).getCodigoProducto());
    }

    // ------------------------------------------------------------------

    private List<String> codigosDePagina(int pagina) {
        return productoService.listarPaginado(null, null, null, null, pagina, TAMANIO_PAGINA)
                .getContent().stream()
                .map(ProductoResponse::getCodigoProducto)
                .collect(Collectors.toList());
    }

    private <T extends Auditable> T auditar(T entidad, java.util.function.Consumer<T> completar) {
        completar.accept(entidad);
        entidad.setUserCreate("test");
        entidad.setProcessCreate("TEST");
        entidad.setDateCreate(LocalDateTime.now());
        return entidad;
    }
}
