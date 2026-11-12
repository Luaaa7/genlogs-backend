package com.genlogs.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
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
 * BE-B · Confirmar N.º de parte y equivalentes (D-01: el Asistente Logístico completa o corrige
 * marca y N.º de parte y agrega equivalentes antes de buscar) y vista del cliente solo con estados
 * (D-14: el cliente ve la barra Recibida → En búsqueda → En espera de proveedor → Cotizada, sin
 * plazos, fechas internas, motivos de pausa ni responsable).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(ClientesDePrueba.class)
class IdentificacionVistaClienteTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private ClientesDePrueba datos;

    private String cliente;
    private String jefe;

    @BeforeEach
    void preparar() {
        cliente = datos.nuevoCliente();
        jefe = datos.nuevoInterno("JEFE_ADMINISTRATIVO");
    }

    // ================================================================ confirmar N.º de parte

    @Test
    void confirmar_corrigeNumeroDeParteYGuardaEquivalentes() throws Exception {
        long id = crearPedidoSinNumeroDeParte();
        long item = primerItem(id);

        mockMvc.perform(put("/api/solicitudes/" + id + "/items/" + item + "/numero-parte")
                        .with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"marcaTexto":"Caterpillar","numeroParte":"1R-0750","gtin":"7750182002011",
                                 "equivalentes":["P551808","LF16035"," p551808 ","1R-0750"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroParte").value("1R-0750"))
                .andExpect(jsonPath("$.marca").value("Caterpillar"))
                .andExpect(jsonPath("$.confirmado").value(true))
                .andExpect(jsonPath("$.confirmadoPor").value(jefe))
                .andExpect(jsonPath("$.equivalentes.length()").value(2));

        em.flush();
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT numero_parte, marca_texto, parte_confirmada, id_usuario_confirma FROM solicitud_item WHERE id_solicitud_item = ?", item);
        assertThat(fila.get("NUMERO_PARTE")).isEqualTo("1R-0750");
        assertThat(fila.get("PARTE_CONFIRMADA")).isEqualTo(true);
        assertThat(jdbc.queryForList("SELECT numero_parte FROM solicitud_item_equivalente WHERE id_solicitud_item = ? ORDER BY numero_parte",
                String.class, item)).containsExactly("LF16035", "P551808");
        assertThat(jdbc.queryForObject("SELECT detalle FROM solicitud_evento WHERE id_solicitud = ? AND tipo_evento = 'EDICION'",
                String.class, id)).contains("1R-0750", "Caterpillar");
    }

    @Test
    void confirmarSinMarca_responde400() throws Exception {
        long id = crearPedidoSinNumeroDeParte();
        mockMvc.perform(put("/api/solicitudes/" + id + "/items/" + primerItem(id) + "/numero-parte")
                        .with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"numeroParte\":\"1R-0750\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("marca")));
    }

    @Test
    void confirmarConGtinInvalido_responde400() throws Exception {
        long id = crearPedidoSinNumeroDeParte();
        mockMvc.perform(put("/api/solicitudes/" + id + "/items/" + primerItem(id) + "/numero-parte")
                        .with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marcaTexto\":\"CAT\",\"numeroParte\":\"1R-0750\",\"gtin\":\"12AB\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void masDe10Equivalentes_responde400() throws Exception {
        long id = crearPedidoSinNumeroDeParte();
        String once = "[" + String.join(",", java.util.stream.IntStream.rangeClosed(1, 11).mapToObj(i -> "\"EQ" + i + "\"").toList()) + "]";
        mockMvc.perform(put("/api/solicitudes/" + id + "/items/" + primerItem(id) + "/numero-parte")
                        .with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marcaTexto\":\"CAT\",\"numeroParte\":\"1R-0750\",\"equivalentes\":" + once + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void itemDeOtraSolicitud_responde404() throws Exception {
        long id = crearPedidoSinNumeroDeParte();
        long otra = crearPedidoSinNumeroDeParte();
        mockMvc.perform(put("/api/solicitudes/" + id + "/items/" + primerItem(otra) + "/numero-parte")
                        .with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"marcaTexto\":\"CAT\",\"numeroParte\":\"1R\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void enUnaSolicitudCancelada_responde409() throws Exception {
        long id = crearPedidoSinNumeroDeParte();
        jdbc.update("UPDATE solicitud_cotizacion SET estado_solicitud = 'CANCELADA' WHERE id_solicitud = ?", id);
        em.clear();
        mockMvc.perform(put("/api/solicitudes/" + id + "/items/" + primerItem(id) + "/numero-parte")
                        .with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"marcaTexto\":\"CAT\",\"numeroParte\":\"1R\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void elClienteNoConfirmaNumerosDeParte() throws Exception {
        long id = crearPedidoSinNumeroDeParte();
        mockMvc.perform(put("/api/solicitudes/" + id + "/items/" + primerItem(id) + "/numero-parte")
                        .with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"marcaTexto\":\"CAT\",\"numeroParte\":\"1R\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void itemsInternos_muestranLoQueEscribioElClienteYLoConfirmado() throws Exception {
        long id = crearPedidoSinNumeroDeParte();
        mockMvc.perform(get("/api/solicitudes/" + id + "/items").with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].descripcion").value("Pieza rota del chancador"))
                .andExpect(jsonPath("$[0].urlFoto").value("https://res.cloudinary.com/x/pieza.jpg"))
                .andExpect(jsonPath("$[0].confirmado").value(false))
                .andExpect(jsonPath("$[0].equivalentes.length()").value(0));
    }

    // ================================================================ vista del cliente

    @Test
    void cliente_veSuPedidoConEtapaYSinNadaInterno() throws Exception {
        long id = crearPedidoSinNumeroDeParte();
        jdbc.update("UPDATE solicitud_cotizacion SET estado_solicitud = 'EN_ESPERA_TERCERO', pausada_desde = ?, "
                        + "motivo_pausa = 'Proveedor confirma stock', fecha_estimada_tercero = ?, "
                        + "id_usuario_asignado = ? WHERE id_solicitud = ?",
                Timestamp.valueOf(LocalDateTime.now()), java.sql.Date.valueOf(LocalDate.now().plusDays(3)),
                datos.idUsuario(jefe), id);
        em.clear();

        String json = mockMvc.perform(get("/api/portal/pedidos/" + id).with(user(cliente).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_ESPERA_TERCERO"))
                .andExpect(jsonPath("$.etapa").value("EN_ESPERA_PROVEEDOR"))
                .andExpect(jsonPath("$.puedeCancelar").value(false))
                .andExpect(jsonPath("$.items[0].urlFoto").value("https://res.cloudinary.com/x/pieza.jpg"))
                .andReturn().getResponse().getContentAsString();

        for (String interno : List.of("fechaLimite", "tipoPlazo", "pausada", "motivoPausa", "fechaEstimada",
                "asignado", "reclasificaciones", "Proveedor confirma", jefe, "equivalentes", "confirmado")) {
            assertThat(json).as("no debe mostrar %s", interno).doesNotContain(interno);
        }
    }

    @Test
    void cliente_veLaEtapaDeCadaEstado() throws Exception {
        long id = crearPedidoSinNumeroDeParte();
        for (String[] caso : new String[][] {
                {"PENDIENTE", "RECIBIDA", "true"}, {"EN_REVISION", "EN_BUSQUEDA", "true"},
                {"COTIZADA", "COTIZADA", "false"}, {"CANCELADA", "CANCELADA", "false"}}) {
            jdbc.update("UPDATE solicitud_cotizacion SET estado_solicitud = ? WHERE id_solicitud = ?", caso[0], id);
            em.clear();
            mockMvc.perform(get("/api/portal/pedidos/" + id).with(user(cliente).roles("CLIENTE")))
                    .andExpect(jsonPath("$.etapa").value(caso[1]))
                    .andExpect(jsonPath("$.puedeCancelar").value(Boolean.parseBoolean(caso[2])));
        }
    }

    @Test
    void cliente_veLaMarcaYElNumeroDeParteDeSusRepuestos() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/solicitudes").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"canal":"PORTAL","asunto":"Filtros",
                                 "items":[{"descripcion":"Filtro","cantidad":2,"marcaTexto":"CAT","numeroParte":"1R-0750","gtin":"7750182002011"}]}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(cuerpo.replaceAll(".*\"idSolicitud\":(\\d+).*", "$1"));

        mockMvc.perform(get("/api/portal/pedidos").with(user(cliente).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].items[0].marca").value("CAT"))
                .andExpect(jsonPath("$[0].items[0].numeroParte").value("1R-0750"))
                .andExpect(jsonPath("$[0].items[0].gtin").value("7750182002011"));
    }

    // ================================================================ apoyo

    private long crearPedidoSinNumeroDeParte() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/solicitudes").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"canal":"PORTAL","asunto":"Pieza rota",
                                 "items":[{"descripcion":"Pieza rota del chancador","cantidad":1,
                                           "urlFoto":"https://res.cloudinary.com/x/pieza.jpg"}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(cuerpo.replaceAll(".*\"idSolicitud\":(\\d+).*", "$1"));
    }

    private long primerItem(long idSolicitud) {
        em.flush();
        return jdbc.queryForObject("SELECT MIN(id_solicitud_item) FROM solicitud_item WHERE id_solicitud = ?",
                Long.class, idSolicitud);
    }
}
