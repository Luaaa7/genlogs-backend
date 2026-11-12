package com.genlogs.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.support.ClientesDePrueba;

import jakarta.persistence.EntityManager;

/**
 * El cliente cancela su pedido antes de que se cotice (PATCH /api/portal/pedidos/{id}/cancelar).
 * Solo en PENDIENTE o EN_REVISION; después responde 409. Queda en la bitácora (solicitud_evento).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(ClientesDePrueba.class)
class CancelarPedidoTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ClientesDePrueba datos;
    @Autowired private EntityManager em;

    @Test
    void clienteCancelaSuPedidoPendienteConMotivo() throws Exception {
        String cliente = datos.nuevoCliente();
        long id = crearPedido(cliente);

        mockMvc.perform(patch("/api/portal/pedidos/" + id + "/cancelar").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Ya lo conseguimos en Lima\"}"))
                .andExpect(status().isNoContent());

        assertThat(estado(id)).isEqualTo("CANCELADA");
        em.flush();
        Map<String, Object> evento = jdbc.queryForMap(
                "SELECT tipo_evento, detalle, id_usuario FROM solicitud_evento WHERE id_solicitud = ? AND tipo_evento = 'ESTADO'", id);
        assertThat((String) evento.get("DETALLE")).contains("PENDIENTE", "CANCELADA", "Ya lo conseguimos en Lima");
        assertThat(((Number) evento.get("ID_USUARIO")).longValue()).isEqualTo(datos.idUsuario(cliente));
    }

    @Test
    void sinMotivoTambienSeCancela() throws Exception {
        String cliente = datos.nuevoCliente();
        long id = crearPedido(cliente);

        mockMvc.perform(patch("/api/portal/pedidos/" + id + "/cancelar").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":null}"))
                .andExpect(status().isNoContent());

        assertThat(estado(id)).isEqualTo("CANCELADA");
    }

    @Test
    void enRevisionTodaviaSePuedeCancelar() throws Exception {
        String cliente = datos.nuevoCliente();
        long id = crearPedido(cliente);
        jdbc.update("UPDATE solicitud_cotizacion SET estado_solicitud = 'EN_REVISION' WHERE id_solicitud = ?", id);
        em.clear();

        mockMvc.perform(patch("/api/portal/pedidos/" + id + "/cancelar").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());

        assertThat(estado(id)).isEqualTo("CANCELADA");
    }

    @Test
    void yaCotizada_responde409YNoCambia() throws Exception {
        String cliente = datos.nuevoCliente();
        long id = crearPedido(cliente);
        jdbc.update("UPDATE solicitud_cotizacion SET estado_solicitud = 'COTIZADA' WHERE id_solicitud = ?", id);
        em.clear();

        mockMvc.perform(patch("/api/portal/pedidos/" + id + "/cancelar").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ya no se puede cancelar")));

        assertThat(estado(id)).isEqualTo("COTIZADA");
    }

    @Test
    void cancelarDosVeces_responde409() throws Exception {
        String cliente = datos.nuevoCliente();
        long id = crearPedido(cliente);
        String url = "/api/portal/pedidos/" + id + "/cancelar";

        mockMvc.perform(patch(url).with(user(cliente).roles("CLIENTE")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch(url).with(user(cliente).roles("CLIENTE")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void pedidoDeOtroCliente_responde404() throws Exception {
        String duena = datos.nuevoCliente();
        String otra = datos.nuevoCliente();
        long id = crearPedido(duena);

        mockMvc.perform(patch("/api/portal/pedidos/" + id + "/cancelar").with(user(otra).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());

        assertThat(estado(id)).isEqualTo("PENDIENTE");
    }

    @Test
    void motivoDeMasDe300_responde400() throws Exception {
        String cliente = datos.nuevoCliente();
        long id = crearPedido(cliente);

        mockMvc.perform(patch("/api/portal/pedidos/" + id + "/cancelar").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"" + "x".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void personalInterno_noCancelaPorElPortal() throws Exception {
        mockMvc.perform(patch("/api/portal/pedidos/1/cancelar").with(user("jefe").roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    private long crearPedido(String cliente) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/solicitudes").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"canal":"PORTAL","asunto":"Filtros",
                                 "items":[{"descripcion":"Filtro","cantidad":1,"marcaTexto":"CAT","numeroParte":"1R-0750"}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(cuerpo.replaceAll(".*\"idSolicitud\":(\\d+).*", "$1"));
    }

    /** La prueba es una sola transacción: se escribe lo pendiente de JPA antes de leer con SQL. */
    private String estado(long id) {
        em.flush();
        return jdbc.queryForObject("SELECT estado_solicitud FROM solicitud_cotizacion WHERE id_solicitud = ?", String.class, id);
    }
}
