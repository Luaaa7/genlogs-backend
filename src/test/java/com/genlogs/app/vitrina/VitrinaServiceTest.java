package com.genlogs.app.vitrina;

import com.genlogs.app.enums.EstadoPublicacion;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.enums.TipoRepuesto;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.CategoriaProducto;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.ProductoImagen;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.repository.*;
import com.genlogs.app.service.NotificacionService;
import com.genlogs.app.service.ProductoService;
import com.genlogs.app.sourcing.EstadoSourcing;
import com.genlogs.app.sourcing.HistorialSourcing;
import com.genlogs.app.sourcing.HistorialSourcingRepository;
import com.genlogs.app.vitrina.dto.ConvertirProductoRequest;
import com.genlogs.app.vitrina.dto.ProductoVitrinaResponse;
import com.genlogs.app.vitrina.dto.RepuestoPublicoResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VitrinaServiceTest {

    private ProductoRepository productoRepo;
    private ProductoImagenRepository imagenRepo;
    private HistorialSourcingRepository historialRepo;
    private NotificacionService notificaciones;
    private MargenRepuesto margen;
    private VitrinaService servicio;

    private final CategoriaProducto filtros = CategoriaProducto.builder().idCategoriaProducto(3).nombreCategoria("Filtros").build();
    private final UnidadMedida unidad = UnidadMedida.builder().idUnidadMedida(1).nombreUnidad("Unidad").build();

    @BeforeEach
    void setUp() {
        productoRepo = mock(ProductoRepository.class);
        imagenRepo = mock(ProductoImagenRepository.class);
        historialRepo = mock(HistorialSourcingRepository.class);
        notificaciones = mock(NotificacionService.class);
        margen = mock(MargenRepuesto.class);
        CategoriaProductoRepository categorias = mock(CategoriaProductoRepository.class);
        UnidadMedidaRepository unidades = mock(UnidadMedidaRepository.class);
        ProductoService productoService = mock(ProductoService.class);

        when(categorias.findById(3)).thenReturn(Optional.of(filtros));
        when(unidades.findById(1)).thenReturn(Optional.of(unidad));
        when(productoService.generarSiguienteCodigo()).thenReturn("GLB-000101");
        when(margen.rangos()).thenReturn(new MargenRepuesto.Rangos(
                new BigDecimal("25"), new BigDecimal("15"), new BigDecimal("35"), new BigDecimal("45")));
        when(productoRepo.save(any())).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            if (p.getIdProducto() == null) p.setIdProducto(101L);
            return p;
        });
        when(historialRepo.findFirstByIdProductoOrderByCreadoEnDesc(anyLong())).thenReturn(Optional.empty());

        servicio = new VitrinaService(productoRepo, imagenRepo, historialRepo, categorias, unidades,
                mock(MarcaRepository.class), mock(UsuarioRepository.class), productoService, margen, notificaciones);
        sesion("asistente", "ASISTENTE_LOGISTICO");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static void sesion(String usuario, String rol) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                usuario, "x", List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    private static HistorialSourcing util(BigDecimal costo) {
        return HistorialSourcing.builder().idSourcing(7L).fuente("AMAZON").numeroParte("1R-0750")
                .origen("IMPORTADO").costoPuestoLima(costo).estado(EstadoSourcing.UTIL).build();
    }

    private static ConvertirProductoRequest pedido(BigDecimal margen) {
        return new ConvertirProductoRequest(7L, "Filtro de combustible CAT 1R-0750", null, 3, 1, null, null,
                TipoRepuesto.OEM, null, null, margen);
    }

    @Test
    void convierteUnaReferenciaUtilEnBorradorConSuPrecio() {
        HistorialSourcing ref = util(new BigDecimal("455.00"));
        when(historialRepo.findById(7L)).thenReturn(Optional.of(ref));

        ProductoVitrinaResponse r = servicio.convertir(pedido(null));

        assertThat(r.estado()).isEqualTo(EstadoPublicacion.BORRADOR);
        assertThat(r.precioReferencial()).isEqualByComparingTo("568.75"); // 455 × 1,25
        assertThat(r.numeroParte()).isEqualTo("1R-0750");
        assertThat(r.categoria()).isEqualTo("Filtros");
        assertThat(r.requiereJefe()).isFalse();
        assertThat(ref.getEstado()).isEqualTo(EstadoSourcing.CONVERTIDO);
        assertThat(ref.getIdProducto()).isEqualTo(101L);
        verifyNoInteractions(notificaciones);
    }

    @Test
    void soloSeConviertenReferenciasUtiles() {
        HistorialSourcing ref = util(new BigDecimal("100"));
        ref.setEstado(EstadoSourcing.CAPTURADO);
        when(historialRepo.findById(7L)).thenReturn(Optional.of(ref));

        assertThatThrownBy(() -> servicio.convertir(pedido(null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ÚTIL");
        verify(productoRepo, never()).save(any());
    }

    @Test
    void sinCostoPuestoEnLimaNoSeConvierte() {
        when(historialRepo.findById(7L)).thenReturn(Optional.of(util(null)));
        assertThatThrownBy(() -> servicio.convertir(pedido(null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("costo puesto en Lima");
    }

    @Test
    void margenFueraDeRango_quedaEnBorradorYAvisaAlJefe() {
        when(historialRepo.findById(7L)).thenReturn(Optional.of(util(new BigDecimal("100"))));

        ProductoVitrinaResponse r = servicio.convertir(pedido(new BigDecimal("60")));

        assertThat(r.estado()).isEqualTo(EstadoPublicacion.BORRADOR);
        verify(notificaciones).avisarRol(eq(RolSistema.JEFE_ADMINISTRATIVO), eq(TipoAviso.MARGEN_FUERA_RANGO),
                anyString(), contains("60"), eq("/vitrina/productos/101"), eq(false));
    }

    private Producto borrador(String precio) {
        Producto p = Producto.builder().idProducto(101L).categoriaProducto(filtros).unidadMedida(unidad)
                .codigoProducto("GLB-000101").nombreProducto("Filtro").precioReferencial(new BigDecimal(precio))
                .estadoPublicacion(EstadoPublicacion.BORRADOR).build();
        when(productoRepo.findById(101L)).thenReturn(Optional.of(p));
        return p;
    }

    private static ProductoImagen imagen(boolean verificada) {
        ProductoImagen i = ProductoImagen.builder().urlImagen("https://res.cloudinary.com/x/1.jpg").esPrincipal(true).build();
        if (verificada) {
            i.setFuenteImagen(FuenteImagen.RECEPCION);
            i.setVerificadaEn(LocalDateTime.now());
        }
        return i;
    }

    @Test
    void noSePublicaSinFotoVerificada() {
        borrador("125");
        when(imagenRepo.findByProducto_IdProductoAndStatus(101L, "A")).thenReturn(List.of(imagen(false)));

        assertThatThrownBy(() -> servicio.publicar(101L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("foto propia verificada");
    }

    @Test
    void conFotoVerificadaSePublica() {
        Producto p = borrador("125");
        when(imagenRepo.findByProducto_IdProductoAndStatus(101L, "A")).thenReturn(List.of(imagen(true)));

        assertThat(servicio.publicar(101L).estado()).isEqualTo(EstadoPublicacion.PUBLICADO);
        assertThat(p.getVisibleWeb()).isTrue();
        assertThat(p.getFechaUltimaVerificacion()).isNotNull();
    }

    @Test
    void margenFueraDeRango_soloLoPublicaElJefe() {
        borrador("160"); // costo 100 → margen 60 %
        when(imagenRepo.findByProducto_IdProductoAndStatus(101L, "A")).thenReturn(List.of(imagen(true)));
        when(historialRepo.findFirstByIdProductoOrderByCreadoEnDesc(101L))
                .thenReturn(Optional.of(util(new BigDecimal("100"))));

        assertThatThrownBy(() -> servicio.publicar(101L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Jefe Administrativo");

        sesion("jefe", "JEFE_ADMINISTRATIVO");
        assertThat(servicio.publicar(101L).estado()).isEqualTo(EstadoPublicacion.PUBLICADO);
    }

    @Test
    void retiradoNoVuelveAPublicarse() {
        borrador("125").setEstadoPublicacion(EstadoPublicacion.RETIRADO);
        assertThatThrownBy(() -> servicio.publicar(101L)).isInstanceOf(BusinessException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void elCatalogoPublicoSoloMuestraFotosVerificadas() {
        Producto p = Producto.builder().idProducto(101L).categoriaProducto(filtros).unidadMedida(unidad)
                .codigoProducto("GLB-000101").nombreProducto("Filtro").precioReferencial(new BigDecimal("125"))
                .estadoPublicacion(EstadoPublicacion.PUBLICADO).build();
        ProductoImagen deTienda = imagen(false);
        deTienda.setProducto(p);
        when(productoRepo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(p));
        when(imagenRepo.findByProducto_IdProductoInAndStatus(anyCollection(), eq("A"))).thenReturn(List.of(deTienda));

        List<RepuestoPublicoResponse> lista = servicio.catalogoPublico(null, null, null);

        assertThat(lista).singleElement().satisfies(r -> {
            assertThat(r.urlImagen()).isNull();
            assertThat(r.plazoEntrega()).isEqualTo(VitrinaService.PLAZO_NACIONAL);
        });
    }

    @Test
    void soloSePuedeOcultarORetirarDesdeCambiarEstado() {
        borrador("125");
        assertThatThrownBy(() -> servicio.cambiarEstado(101L, EstadoPublicacion.PUBLICADO))
                .isInstanceOf(BusinessException.class);

        ArgumentCaptor<Producto> captor = ArgumentCaptor.forClass(Producto.class);
        when(imagenRepo.findByProducto_IdProductoAndStatus(101L, "A")).thenReturn(List.of());
        servicio.cambiarEstado(101L, EstadoPublicacion.OCULTO);
        verify(productoRepo).save(captor.capture());
        assertThat(captor.getValue().getEstadoPublicacion()).isEqualTo(EstadoPublicacion.OCULTO);
        assertThat(captor.getValue().getVisibleWeb()).isFalse();
    }
}
