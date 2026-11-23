package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.genlogs.app.dto.CotizacionDetalleRequest;
import com.genlogs.app.dto.CotizacionDetalleResponse;
import com.genlogs.app.enums.OrigenRepuesto;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.CotizacionDetalle;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.repository.CotizacionDetalleRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.UnidadMedidaRepository;

@ExtendWith(MockitoExtension.class)
class OrigenLineaCotizacionTest {

    @Mock private ProductoRepository productos;
    @Mock private CotizacionDetalleRepository detalles;
    @Mock private UnidadMedidaRepository unidades;
    @InjectMocks private CotizacionService servicio;

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void crearConOrigenImportadoLoGuarda() {
        when(productos.findById(7L)).thenReturn(Optional.of(producto(7L)));

        CotizacionDetalle linea = servicio.construirLinea(
                new Cotizacion(), pedido(7L, OrigenRepuesto.IMPORTADO), "ana");

        assertEquals(OrigenRepuesto.IMPORTADO, linea.getOrigen());
        verify(productos, never()).findOrigenById(any());
    }

    @Test
    void sinOrigenCopiaElDelProducto() {
        when(productos.findById(7L)).thenReturn(Optional.of(producto(7L)));
        when(productos.findOrigenById(7L)).thenReturn(List.of("IMPORTADO"));

        CotizacionDetalle linea = servicio.construirLinea(new Cotizacion(), pedido(7L, null), "ana");

        assertEquals(OrigenRepuesto.IMPORTADO, linea.getOrigen());
    }

    @Test
    void sinOrigenNiProductoQuedaNulo() {
        CotizacionDetalleRequest req = new CotizacionDetalleRequest();
        req.setDescripcion("filtro a medida");
        req.setCantidad(BigDecimal.ONE);
        req.setPrecioUnitario(BigDecimal.TEN);
        UnidadMedida unidad = new UnidadMedida();
        unidad.setIdUnidadMedida(1);
        when(unidades.findFirstByStatusOrderByIdUnidadMedidaAsc("A")).thenReturn(Optional.of(unidad));

        CotizacionDetalle linea = servicio.construirLinea(new Cotizacion(), req, "ana");

        assertNull(linea.getOrigen());
        verify(productos, never()).findOrigenById(any());
    }

    @Test
    void editarSinOrigenConservaElGuardado() {
        CotizacionDetalle linea = new CotizacionDetalle();
        linea.setOrigen(OrigenRepuesto.IMPORTADO);
        linea.setProducto(producto(7L));

        servicio.aplicarOrigen(linea, pedido(7L, null), true);

        assertEquals(OrigenRepuesto.IMPORTADO, linea.getOrigen());
        verify(productos, never()).findOrigenById(any());
    }

    @Test
    void editarConOrigenNuevoLoReemplaza() {
        CotizacionDetalle linea = new CotizacionDetalle();
        linea.setOrigen(OrigenRepuesto.IMPORTADO);

        servicio.aplicarOrigen(linea, pedido(7L, OrigenRepuesto.NACIONAL), true);

        assertEquals(OrigenRepuesto.NACIONAL, linea.getOrigen());
    }

    @Test
    void laVistaInternaIncluyeElOrigen() {
        CotizacionDetalle linea = new CotizacionDetalle();
        linea.setIdCotizacionDetalle(3L);
        linea.setOrigen(OrigenRepuesto.IMPORTADO);
        linea.setCantidad(BigDecimal.ONE);
        linea.setPrecioUnitario(BigDecimal.TEN);
        when(detalles.findByCotizacion(1L)).thenReturn(List.of(linea));

        assertEquals(OrigenRepuesto.IMPORTADO, servicio.listarLineas(1L).get(0).getOrigen());
    }

    @Test
    void unRolSinMargenRecibeCostoYMargenEnNull() {
        CotizacionDetalle linea = new CotizacionDetalle();
        linea.setIdCotizacionDetalle(3L);
        linea.setCantidad(BigDecimal.ONE);
        linea.setPrecioUnitario(new BigDecimal("150"));
        linea.setCostoUnitario(new BigDecimal("100"));
        linea.setMargenPorcentaje(new BigDecimal("20"));
        when(detalles.findByCotizacion(1L)).thenReturn(List.of(linea));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "cliente", "n/a", List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));

        CotizacionDetalleResponse oculta = servicio.listarLineas(1L).get(0);

        assertNull(oculta.getCostoUnitario());
        assertNull(oculta.getMargenPorcentaje());
        assertEquals(0, new BigDecimal("150").compareTo(oculta.getPrecioUnitario()));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "jefe", "n/a", List.of(new SimpleGrantedAuthority("ROLE_JEFE_ADMINISTRATIVO"))));
        CotizacionDetalleResponse visible = servicio.listarLineas(1L).get(0);
        assertEquals(0, new BigDecimal("100").compareTo(visible.getCostoUnitario()));
        assertEquals(0, new BigDecimal("20").compareTo(visible.getMargenPorcentaje()));
    }

    private static CotizacionDetalleRequest pedido(Long idProducto, OrigenRepuesto origen) {
        CotizacionDetalleRequest req = new CotizacionDetalleRequest();
        req.setIdProducto(idProducto);
        req.setCantidad(BigDecimal.ONE);
        req.setPrecioUnitario(BigDecimal.TEN);
        req.setOrigen(origen);
        return req;
    }

    private static Producto producto(Long id) {
        UnidadMedida unidad = new UnidadMedida();
        unidad.setIdUnidadMedida(1);
        Producto producto = new Producto();
        producto.setIdProducto(id);
        producto.setUnidadMedida(unidad);
        return producto;
    }
}
