package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.genlogs.app.dto.PrecargaOrdenCompraResponse;
import com.genlogs.app.exception.EstadoNoPermitidoException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.CotizacionDetalle;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.repository.CotizacionDetalleRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.OrdenCompraRepository;

@ExtendWith(MockitoExtension.class)
class PrecargaOrdenCompraTest {

    @Mock private OrdenCompraRepository ordenes;
    @Mock private CotizacionRepository cotizaciones;
    @Mock private CotizacionDetalleRepository detalles;

    @Test
    void sinAceptacionResponde409() {
        when(cotizaciones.findById(3L)).thenReturn(Optional.of(cotizacion("ENVIADA", "PEN", null)));

        EstadoNoPermitidoException error = assertThrows(EstadoNoPermitidoException.class,
                () -> servicio().precargarDesdeCotizacion(3L));

        assertEquals(true, error.getMessage().contains("aceptó"));
    }

    @Test
    void precargaEnSolesElClienteLosItemsYLosMontos() {
        Cotizacion cotizacion = cotizacion("ACEPTADA_CLIENTE", "PEN", null);
        when(cotizaciones.findById(3L)).thenReturn(Optional.of(cotizacion));
        when(cotizaciones.findTotalesByCotizacion(3L)).thenReturn(Optional.of(totales("100.00", "18.00", "118.00")));
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(linea()));

        PrecargaOrdenCompraResponse respuesta = servicio().precargarDesdeCotizacion(3L);

        assertEquals(3L, respuesta.getIdCotizacion());
        assertEquals("Mina Sur", respuesta.getCliente());
        assertEquals("PEN", respuesta.getMoneda());
        assertEquals("PEN", respuesta.getMonedaCotizacion());
        assertNull(respuesta.getTipoCambio());
        assertEquals(new BigDecimal("118.00"), respuesta.getTotal());
        assertEquals("Filtro", respuesta.getItems().get(0).getDescripcion());
        assertEquals(new BigDecimal("10.0000"), respuesta.getItems().get(0).getPrecioUnitario());
    }

    @Test
    void dolaresSeConviertenASolesConElTipoDeCambioDeLaCotizacion() {
        Cotizacion cotizacion = cotizacion("APROBADA", "USD", new BigDecimal("3.75"));
        when(cotizaciones.findById(3L)).thenReturn(Optional.of(cotizacion));
        when(cotizaciones.findTotalesByCotizacion(3L)).thenReturn(Optional.of(totales("100.00", "18.00", "118.00")));
        when(detalles.findByCotizacion(3L)).thenReturn(List.of(linea()));

        PrecargaOrdenCompraResponse respuesta = servicio().precargarDesdeCotizacion(3L);

        assertEquals("PEN", respuesta.getMoneda());
        assertEquals("USD", respuesta.getMonedaCotizacion());
        assertEquals(new BigDecimal("3.75"), respuesta.getTipoCambio());
        assertEquals(new BigDecimal("442.50"), respuesta.getTotal());
        assertEquals(new BigDecimal("37.50"), respuesta.getItems().get(0).getPrecioUnitario());
    }

    private OrdenCompraService servicio() {
        return new OrdenCompraService(ordenes, cotizaciones, null, null, null, null, null, null, detalles);
    }

    private static Cotizacion cotizacion(String estadoCodigo, String monedaCodigo, BigDecimal tipoCambio) {
        Tercero tercero = new Tercero();
        tercero.setRazonSocial("Mina Sur");
        Cliente cliente = new Cliente();
        cliente.setIdCliente(8L);
        cliente.setTercero(tercero);
        Moneda moneda = new Moneda();
        moneda.setCodigoMoneda(monedaCodigo);
        EstadoCotizacion estado = new EstadoCotizacion();
        estado.setCodigoEstado(estadoCodigo);
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(3L);
        cotizacion.setCodigoCotizacion("COT-3");
        cotizacion.setCliente(cliente);
        cotizacion.setMoneda(moneda);
        cotizacion.setEstadoCotizacion(estado);
        cotizacion.setTipoCambioUsado(tipoCambio);
        return cotizacion;
    }

    private static CotizacionDetalle linea() {
        UnidadMedida unidad = new UnidadMedida();
        unidad.setNombreUnidad("UND");
        CotizacionDetalle linea = new CotizacionDetalle();
        linea.setIdCotizacionDetalle(11L);
        linea.setDescripcionPersonalizada("Filtro");
        linea.setCantidad(new BigDecimal("2"));
        linea.setPrecioUnitario(new BigDecimal("10.0000"));
        linea.setImporteLinea(new BigDecimal("20.00"));
        linea.setUnidadMedida(unidad);
        return linea;
    }

    private static CotizacionRepository.TotalesCotizacionProjection totales(String subtotal, String igv, String total) {
        return new CotizacionRepository.TotalesCotizacionProjection() {
            @Override
            public BigDecimal getSubtotal() {
                return new BigDecimal(subtotal);
            }

            @Override
            public BigDecimal getIgv() {
                return new BigDecimal(igv);
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(total);
            }
        };
    }
}
