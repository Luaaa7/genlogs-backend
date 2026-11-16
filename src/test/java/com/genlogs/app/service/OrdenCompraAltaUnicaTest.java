package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.genlogs.app.dto.OrdenCompraRequest;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.OrdenCompraRepository;

@ExtendWith(MockitoExtension.class)
class OrdenCompraAltaUnicaTest {

    @Mock private OrdenCompraRepository ordenes;
    @Mock private CotizacionRepository cotizaciones;

    @Test
    void elAltaManualConUnaOcActivaSeRechazaYNoGuarda() {
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(3L);
        EstadoCotizacion estado = new EstadoCotizacion();
        estado.setCodigoEstado("APROBADA");
        cotizacion.setEstadoCotizacion(estado);
        when(cotizaciones.findById(3L)).thenReturn(Optional.of(cotizacion));
        when(ordenes.existsByCotizacion_IdCotizacionAndStatus(3L, "A")).thenReturn(true);
        OrdenCompraRequest request = new OrdenCompraRequest();
        request.setIdCotizacion(3L);
        request.setNumeroOrdenCompra("0099-10");

        BusinessException error = assertThrows(BusinessException.class, () -> servicio().crear(request));

        assertEquals("Esta cotización ya tiene una orden de compra registrada", error.getMessage());
        verify(ordenes, never()).save(any());
    }

    private OrdenCompraService servicio() {
        return new OrdenCompraService(ordenes, cotizaciones, null, null, null, null, null, null);
    }
}
