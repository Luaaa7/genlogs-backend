package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.model.SolicitudCotizacion;

/**
 * El cliente solo recibe el enlace a su cotización cuando ya se le envió: mientras esté en borrador,
 * pendiente de aprobación o aprobada por el Jefe (sin enviar), el pedido no trae idCotizacion.
 */
class PedidoCotizacionVisibleTest {

    @Test
    void sinCotizacionNoHayEnlace() {
        assertNull(PortalClienteService.idCotizacionVisible(new SolicitudCotizacion()));
    }

    @Test
    void mientrasSeRevisaInternamenteNoSeMuestra() {
        for (String codigo : new String[] {"BORRADOR", "PENDIENTE_APROBACION", "APROBADA_INTERNA"}) {
            assertNull(PortalClienteService.idCotizacionVisible(conCotizacion(codigo)), codigo);
        }
    }

    @Test
    void desdeQueSeEnviaAlClienteSeMuestra() {
        for (String codigo : new String[] {"ENVIADA", "EN_NEGOCIACION", "ACEPTADA_CLIENTE", "RECHAZADA", "VENCIDA"}) {
            assertEquals(50L, PortalClienteService.idCotizacionVisible(conCotizacion(codigo)), codigo);
        }
    }

    private static SolicitudCotizacion conCotizacion(String codigoEstado) {
        EstadoCotizacion estado = new EstadoCotizacion();
        estado.setCodigoEstado(codigoEstado);
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(50L);
        cotizacion.setEstadoCotizacion(estado);
        SolicitudCotizacion solicitud = new SolicitudCotizacion();
        solicitud.setCotizacion(cotizacion);
        return solicitud;
    }
}
