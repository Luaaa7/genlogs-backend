package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.genlogs.app.dto.AdjuntoCotizacionResponse;
import com.genlogs.app.model.AdjuntoCotizacion;
import com.genlogs.app.repository.AdjuntoCotizacionRepository;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.CondicionPagoRepository;
import com.genlogs.app.repository.ContactoTerceroRepository;
import com.genlogs.app.repository.CotizacionDetalleRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.EstadoCotizacionRepository;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.SectorEconomicoRepository;
import com.genlogs.app.repository.SeguimientoCotizacionRepository;
import com.genlogs.app.repository.ServicioRepository;
import com.genlogs.app.repository.TipoCambioRepository;
import com.genlogs.app.repository.UnidadMedidaRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.pdf.PdfGenerationService;

import tools.jackson.databind.json.JsonMapper;

import jakarta.persistence.EntityManager;
import org.springframework.transaction.support.TransactionTemplate;

class AdjuntoFechaSubidaTest {

    private AdjuntoCotizacionRepository adjuntos;
    private CotizacionService servicio;

    @BeforeEach
    void setUp() {
        adjuntos = mock(AdjuntoCotizacionRepository.class);
        servicio = new CotizacionService(
                mock(CotizacionRepository.class),
                mock(CotizacionDetalleRepository.class),
                mock(EstadoCotizacionRepository.class),
                mock(SeguimientoCotizacionRepository.class),
                mock(PdfGenerationService.class),
                adjuntos,
                mock(ClienteRepository.class),
                mock(ContactoTerceroRepository.class),
                mock(UsuarioRepository.class),
                mock(MonedaRepository.class),
                mock(CondicionPagoRepository.class),
                mock(SectorEconomicoRepository.class),
                mock(ProductoRepository.class),
                mock(ServicioRepository.class),
                mock(UnidadMedidaRepository.class),
                mock(CloudinaryService.class),
                mock(MailService.class),
                mock(TasaIgvService.class),
                mock(TipoCambioCotizacion.class),
                mock(AvisoAltoValorCotizacion.class),
                mock(OrdenCompraService.class),
                mock(TransactionTemplate.class),
                mock(EntityManager.class),
                mock(AvisoOrdenNoCreada.class),
                mock(TipoCambioRepository.class));
    }

    @Test
    void fechaSubidaSaleComoInstantConZona() throws Exception {
        LocalDateTime subido = LocalDateTime.of(2026, 11, 16, 10, 0);
        Instant esperado = subido.atZone(ZoneId.systemDefault()).toInstant();
        AdjuntoCotizacion adjunto = new AdjuntoCotizacion();
        adjunto.setIdAdjunto(3L);
        adjunto.setNombreArchivo("correo-minera.pdf");
        adjunto.setUrlArchivo("https://ejemplo/correo.pdf");
        adjunto.setUserCreate("jefe");
        adjunto.setDateCreate(subido);
        when(adjuntos.findByCotizacion(15L)).thenReturn(List.of(adjunto));

        AdjuntoCotizacionResponse respuesta = servicio.listarAdjuntos(15L).get(0);
        assertEquals(esperado, respuesta.getFechaSubida());

        String json = JsonMapper.builder().build().writeValueAsString(respuesta);

        assertTrue(json.contains(esperado.toString()), json);

        adjunto.setDateCreate(null);
        assertNull(servicio.listarAdjuntos(15L).get(0).getFechaSubida());
    }
}
