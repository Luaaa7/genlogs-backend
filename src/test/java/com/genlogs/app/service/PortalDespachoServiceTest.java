package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.genlogs.app.dto.PortalDespachoResponse;
import com.genlogs.app.enums.EtapaDespacho;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.Despacho;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.DespachoRepository;
import com.genlogs.app.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class PortalDespachoServiceTest {

    @Mock private DespachoRepository despachos;
    @Mock private UsuarioRepository usuarios;

    @BeforeEach
    void setUp() {
        Cliente mio = new Cliente();
        mio.setIdCliente(2L);
        Usuario yo = new Usuario();
        yo.setNombreUsuario("mina");
        yo.setCliente(mio);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("mina", "n/a", java.util.List.of()));
        when(usuarios.findWithClienteByNombreUsuario("mina")).thenReturn(Optional.of(yo));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void otraEmpresaEsElMismo404QueUnoInexistenteYElJsonNoTraeDatosInternos() throws Exception {
        PortalDespachoService servicio = new PortalDespachoService(despachos, usuarios);
        when(despachos.findById(1L)).thenReturn(Optional.empty());
        when(despachos.findById(2L)).thenReturn(Optional.of(despacho(9L)));

        String ausente = assertThrows(ResourceNotFoundException.class, () -> servicio.detalle(1L)).getMessage();
        String ajeno = assertThrows(ResourceNotFoundException.class, () -> servicio.detalle(2L)).getMessage();
        assertEquals(ausente, ajeno);

        when(despachos.findById(3L)).thenReturn(Optional.of(despacho(2L)));
        String json = jsonDe(servicio.detalle(3L));

        assertTrue(json.contains("0008-10"));
        assertTrue(json.contains("En preparación"));
        assertTrue(json.contains("EN_PREPARACION"));
        for (String interno : new String[] {
                "origenIncompleto", "idUsuario", "usuario", "fechaPreparacion", "fechaAduana",
                "fechaTransito", "fechaEntrega", "userCreate", "userUpdate", "dateCreate",
                "processCreate", "origen"}) {
            assertFalse(json.contains(interno), json);
        }
    }

    private static String jsonDe(PortalDespachoResponse respuesta) throws Exception {
        SimpleModule fechas = new SimpleModule();
        fechas.addSerializer(LocalDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
                    throws java.io.IOException {
                gen.writeString(value.toString());
            }
        });
        return new ObjectMapper().registerModule(fechas).writeValueAsString(respuesta);
    }

    private static Despacho despacho(Long idCliente) {
        Cliente cliente = new Cliente();
        cliente.setIdCliente(idCliente);
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setCliente(cliente);
        OrdenCompra orden = new OrdenCompra();
        orden.setNumeroOrdenCompra("0008-10");
        Despacho despacho = new Despacho();
        despacho.setIdDespacho(3L);
        despacho.setCotizacion(cotizacion);
        despacho.setOrdenCompra(orden);
        despacho.setEtapa(EtapaDespacho.EN_PREPARACION);
        despacho.setPlazoDesde(LocalDateTime.of(2026, 3, 14, 10, 0));
        despacho.setPlazoHasta(LocalDateTime.of(2026, 3, 15, 10, 0));
        despacho.setStatus("A");
        Usuario interno = new Usuario();
        interno.setIdUsuario(99L);
        interno.setNombreUsuario("logistica");
        despacho.setUsuario(interno);
        despacho.setUserCreate("logistica");
        return despacho;
    }
}
