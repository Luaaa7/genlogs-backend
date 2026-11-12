package com.genlogs.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.enums.TipoPlazo;
import com.genlogs.app.service.CalendarioHabil;
import com.genlogs.app.service.GestionSolicitudService;
import com.genlogs.app.support.ClientesDePrueba;

import jakarta.persistence.EntityManager;

/**
 * BE-B · Pausa por tercero, reanudar, reclasificar, reasignar e historial (decisión 9 de la v3.1).
 * El reloj interno se detiene en "En espera de tercero" y al reanudar el plazo se corre lo pausado.
 * Reclasificar: una vez el asistente; la segunda solo el Jefe Adm. Reasignar: solo el Jefe Adm.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(ClientesDePrueba.class)
class GestionSolicitudTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private ClientesDePrueba datos;
    @Autowired private CalendarioHabil calendario;
    @Autowired private GestionSolicitudService gestion;

    private String jefe;
    private String cliente;

    @BeforeEach
    void preparar() {
        jefe = datos.nuevoInterno("JEFE_ADMINISTRATIVO");
        cliente = datos.nuevoCliente();
    }

    // ---------------------------------------------------------------- iniciar

    @Test
    void iniciar_pasaAEnRevisionYSeLaAsignaAQuienLaToma() throws Exception {
        long id = crearPedido();

        mockMvc.perform(patch("/api/solicitudes/" + id + "/iniciar").with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"))
                .andExpect(jsonPath("$.asignado.nombreUsuario").value(jefe));

        assertThat(fila(id).get("ID_USUARIO_ASIGNADO")).isNotNull();
        assertThat(eventos(id)).contains("ESTADO");
    }

    // ---------------------------------------------------------------- pausar / reanudar

    @Test
    void pausar_pasaAEsperaDeTerceroConMotivoYFechaEstimada() throws Exception {
        long id = crearPedidoEnRevision();
        LocalDate estimada = LocalDate.now().plusDays(4);

        mockMvc.perform(patch("/api/solicitudes/" + id + "/pausar").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Proveedor confirma stock\",\"fechaEstimada\":\"" + estimada + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_ESPERA_TERCERO"))
                .andExpect(jsonPath("$.motivoPausa").value("Proveedor confirma stock"));

        Map<String, Object> s = fila(id);
        assertThat(s.get("PAUSADA_DESDE")).isNotNull();
        assertThat(s.get("FECHA_ESTIMADA_TERCERO").toString()).isEqualTo(estimada.toString());
        assertThat(eventos(id)).contains("PAUSA");
    }

    @Test
    void pausarSinMotivo_responde400() throws Exception {
        long id = crearPedidoEnRevision();
        mockMvc.perform(patch("/api/solicitudes/" + id + "/pausar").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pausarUnaPendiente_responde409() throws Exception {
        long id = crearPedido();
        mockMvc.perform(patch("/api/solicitudes/" + id + "/pausar").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Proveedor\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void reanudar_corrigeElPlazoConLoQueEstuvoPausado() throws Exception {
        long id = crearPedidoEnRevision();
        // Pausada el lunes 9/11 a las 10:00 con plazo hasta el martes 10/11 al cierre:
        // le quedaban 8 h del lunes + 10 h del martes = 1080 minutos hábiles.
        LocalDateTime pausada = LocalDateTime.of(2026, 11, 9, 10, 0);
        LocalDateTime limite = LocalDateTime.of(2026, 11, 10, 18, 0);
        jdbc.update("UPDATE solicitud_cotizacion SET estado_solicitud = 'EN_ESPERA_TERCERO', pausada_desde = ?, "
                + "motivo_pausa = 'Proveedor', fecha_limite_interna = ? WHERE id_solicitud = ?",
                Timestamp.valueOf(pausada), Timestamp.valueOf(limite), id);
        em.clear();

        LocalDateTime antes = LocalDateTime.now();
        mockMvc.perform(patch("/api/solicitudes/" + id + "/reanudar").with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"));
        LocalDateTime despues = LocalDateTime.now();

        Map<String, Object> s = fila(id);
        LocalDateTime nuevoLimite = ((Timestamp) s.get("FECHA_LIMITE_INTERNA")).toLocalDateTime();
        assertThat(nuevoLimite).isBetween(calendario.sumarMinutosHabiles(antes, 1080).minusSeconds(1),
                calendario.sumarMinutosHabiles(despues, 1080).plusSeconds(1));
        assertThat(s.get("PAUSADA_DESDE")).isNull();
        assertThat(s.get("MOTIVO_PAUSA")).isNull();
        assertThat(eventos(id)).contains("REANUDA");
    }

    @Test
    void reanudarUnaQueNoEstaPausada_responde409() throws Exception {
        long id = crearPedidoEnRevision();
        mockMvc.perform(patch("/api/solicitudes/" + id + "/reanudar").with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isConflict());
    }

    // ---------------------------------------------------------------- reclasificar

    @Test
    void reclasificar_cambiaElTipoYRecalculaElPlazoDesdeLaRecepcion() throws Exception {
        long id = crearPedidoEnRevision();

        mockMvc.perform(patch("/api/solicitudes/" + id + "/reclasificar").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tipoPlazo\":\"IMPORTADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoPlazo").value("IMPORTADO"))
                .andExpect(jsonPath("$.reclasificaciones").value(1));

        Map<String, Object> s = fila(id);
        LocalDateTime recibido = ((Timestamp) s.get("DATE_CREATE")).toLocalDateTime();
        assertThat(((Timestamp) s.get("FECHA_LIMITE_INTERNA")).toLocalDateTime())
                .isEqualTo(calendario.fechaLimite(recibido, 3));
        assertThat(eventos(id)).contains("RECLASIFICA");
    }

    @Test
    void reclasificarAlMismoTipo_responde400() throws Exception {
        long id = crearPedidoEnRevision();
        mockMvc.perform(patch("/api/solicitudes/" + id + "/reclasificar").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tipoPlazo\":\"COMUN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void segundaReclasificacion_elAsistenteNoPuedeYElJefeSi() throws Exception {
        long id = crearPedidoEnRevision();
        String asistente = datos.nuevoInterno("ASISTENTE_LOGISTICO");

        comoUsuario(asistente, "ASISTENTE_LOGISTICO");
        gestion.reclasificar(id, TipoPlazo.IMPORTADO);
        assertThatThrownBy(() -> gestion.reclasificar(id, TipoPlazo.ESPECIAL))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Jefe Administrativo");

        comoUsuario(jefe, "JEFE_ADMINISTRATIVO");
        assertThat(gestion.reclasificar(id, TipoPlazo.ESPECIAL).reclasificaciones()).isEqualTo((short) 2);
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- reasignar

    @Test
    void reasignar_elJefeLaPasaAOtroAsistente() throws Exception {
        long id = crearPedidoEnRevision();
        String otro = datos.nuevoInterno("ASISTENTE_LOGISTICO");

        mockMvc.perform(patch("/api/solicitudes/" + id + "/reasignar").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idUsuario\":" + datos.idUsuario(otro) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asignado.nombreUsuario").value(otro));

        assertThat(eventos(id)).contains("REASIGNA");
    }

    @Test
    void reasignarAUnCliente_responde400() throws Exception {
        long id = crearPedidoEnRevision();
        mockMvc.perform(patch("/api/solicitudes/" + id + "/reasignar").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idUsuario\":" + datos.idUsuario(cliente) + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elAsistenteNoReasigna() {
        String asistente = datos.nuevoInterno("ASISTENTE_LOGISTICO");
        comoUsuario(asistente, "ASISTENTE_LOGISTICO");
        assertThatThrownBy(() -> gestion.reasignar(1L, 1L)).isInstanceOf(AccessDeniedException.class);
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- reglas generales

    @Test
    void sobreUnaCancelada_nadaSePuedeHacer() throws Exception {
        long id = crearPedido();
        jdbc.update("UPDATE solicitud_cotizacion SET estado_solicitud = 'CANCELADA' WHERE id_solicitud = ?", id);
        em.clear();

        mockMvc.perform(patch("/api/solicitudes/" + id + "/iniciar").with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/solicitudes/" + id + "/reclasificar").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tipoPlazo\":\"IMPORTADO\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void elClienteNoUsaEstasRutas() throws Exception {
        long id = crearPedido();
        mockMvc.perform(patch("/api/solicitudes/" + id + "/iniciar").with(user(cliente).roles("CLIENTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/solicitudes/" + id + "/historial").with(user(cliente).roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void historial_muestraCadaCambioEnOrden() throws Exception {
        long id = crearPedidoEnRevision();
        mockMvc.perform(patch("/api/solicitudes/" + id + "/pausar").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Taller\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/solicitudes/" + id + "/historial").with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("CREADA"))
                .andExpect(jsonPath("$[1].tipo").value("ESTADO"))
                .andExpect(jsonPath("$[2].tipo").value("PAUSA"))
                .andExpect(jsonPath("$[2].usuario").value(jefe));
    }

    // ---------------------------------------------------------------- apoyo

    private long crearPedido() throws Exception {
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

    private long crearPedidoEnRevision() throws Exception {
        long id = crearPedido();
        mockMvc.perform(patch("/api/solicitudes/" + id + "/iniciar").with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk());
        return id;
    }

    private Map<String, Object> fila(long id) {
        em.flush();
        return jdbc.queryForMap("SELECT * FROM solicitud_cotizacion WHERE id_solicitud = ?", id);
    }

    private List<String> eventos(long id) {
        em.flush();
        return jdbc.queryForList("SELECT tipo_evento FROM solicitud_evento WHERE id_solicitud = ? ORDER BY id_evento",
                String.class, id);
    }

    private void comoUsuario(String nombre, String rol) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                nombre, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }
}
