package com.genlogs.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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

/** «Le llega al encargado»: al entrar una solicitud, los Asistentes Logísticos reciben un aviso con el enlace. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(ClientesDePrueba.class)
class AvisoSolicitudNuevaTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private ClientesDePrueba datos;

    @Test
    void alCrearseUnaSolicitudElAsistenteLogisticoRecibeUnAvisoConElEnlace() throws Exception {
        String asistente = datos.nuevoInterno("ASISTENTE_LOGISTICO");
        String contador = datos.nuevoInterno("CONTADOR_GENERAL");
        String cliente = datos.nuevoCliente();

        long id = crearSolicitud(cliente);

        List<Map<String, Object>> deAsistente = avisos(asistente);
        assertThat(deAsistente).hasSize(1);
        assertThat(deAsistente.get(0).get("TITULO").toString()).contains("Nueva solicitud");
        assertThat(deAsistente.get(0).get("ENLACE")).isEqualTo("/solicitudes/" + id);
        assertThat(avisos(contador)).isEmpty();
    }

    @Test
    void sinAsistentesActivosLaSolicitudSeCreaIgual() throws Exception {
        String cliente = datos.nuevoCliente();
        crearSolicitud(cliente);
    }

    private long crearSolicitud(String cliente) throws Exception {
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

    private List<Map<String, Object>> avisos(String usuario) {
        em.flush();
        return jdbc.queryForList("SELECT titulo, enlace FROM notificacion WHERE tipo = 'SOLICITUD_NUEVA' AND id_usuario = ?",
                datos.idUsuario(usuario));
    }
}
