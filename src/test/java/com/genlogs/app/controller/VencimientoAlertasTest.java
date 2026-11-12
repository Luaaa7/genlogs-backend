package com.genlogs.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;

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

import com.genlogs.app.service.AlertasPlazoService;
import com.genlogs.app.service.VencimientoSolicitudService;
import com.genlogs.app.service.VencimientoSolicitudService.Semaforo;
import com.genlogs.app.support.ClientesDePrueba;

import jakarta.persistence.EntityManager;

/**
 * BE-B · Lista interna por vencimiento con semáforo y alertas de plazo (decisión 9):
 * al 75 % del plazo se avisa al asistente asignado (o a todos los asistentes si no hay);
 * al vencer, al Jefe Administrativo con correo (D-21). Cada aviso sale una sola vez por plazo.
 *
 * Fechas de noviembre 2026 (el 9 es lunes). Recibida el lunes 9 a las 10:00 con plazo hasta el
 * martes 10 al cierre: 1080 minutos hábiles. El martes a las 15:00 lleva 900 (83 %).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(ClientesDePrueba.class)
class VencimientoAlertasTest {

    private static final LocalDateTime RECIBIDA = LocalDateTime.of(2026, 11, 9, 10, 0);
    private static final LocalDateTime LIMITE = LocalDateTime.of(2026, 11, 10, 18, 0);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private ClientesDePrueba datos;
    @Autowired private VencimientoSolicitudService vencimiento;
    @Autowired private AlertasPlazoService alertas;

    private String cliente;
    private String jefe;
    private String asistente;

    @BeforeEach
    void preparar() {
        cliente = datos.nuevoCliente();
        jefe = datos.nuevoInterno("JEFE_ADMINISTRATIVO");
        asistente = datos.nuevoInterno("ASISTENTE_LOGISTICO");
    }

    // ---------------------------------------------------------------- semáforo

    @Test
    void semaforo_verdeAntesDel75PorCiento() {
        assertThat(vencimiento.semaforo("EN_REVISION", RECIBIDA, LIMITE, LocalDateTime.of(2026, 11, 9, 12, 0)))
                .isEqualTo(Semaforo.VERDE);
    }

    @Test
    void semaforo_amarilloDesdeEl75PorCiento() {
        assertThat(vencimiento.semaforo("EN_REVISION", RECIBIDA, LIMITE, LocalDateTime.of(2026, 11, 10, 15, 0)))
                .isEqualTo(Semaforo.AMARILLO);
    }

    @Test
    void semaforo_rojoSiYaVencio() {
        assertThat(vencimiento.semaforo("EN_REVISION", RECIBIDA, LIMITE, LocalDateTime.of(2026, 11, 11, 9, 0)))
                .isEqualTo(Semaforo.ROJO);
    }

    @Test
    void semaforo_pausadaNoCorre() {
        assertThat(vencimiento.semaforo("EN_ESPERA_TERCERO", RECIBIDA, LIMITE, LocalDateTime.of(2026, 11, 11, 9, 0)))
                .isEqualTo(Semaforo.PAUSADA);
    }

    // ---------------------------------------------------------------- lista

    @Test
    void lista_soloAbiertasYLaMasUrgentePrimero() throws Exception {
        long tranquila = crearPedido(LocalDateTime.of(2030, 1, 10, 18, 0), "PENDIENTE", null);
        long vencida = crearPedido(LocalDateTime.of(2020, 1, 10, 18, 0), "EN_REVISION", null);
        long cotizada = crearPedido(LocalDateTime.of(2019, 1, 10, 18, 0), "COTIZADA", null);

        String json = mockMvc.perform(get("/api/solicitudes/vencimiento").with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.indexOf("\"idSolicitud\":" + vencida)).isLessThan(json.indexOf("\"idSolicitud\":" + tranquila));
        assertThat(json).doesNotContain("\"idSolicitud\":" + cotizada + ",");
        assertThat(json).contains("\"semaforo\":\"ROJO\"", "\"semaforo\":\"VERDE\"");
    }

    @Test
    void lista_soloMias_filtraPorElAsignado() throws Exception {
        long mia = crearPedido(LocalDateTime.of(2030, 1, 10, 18, 0), "EN_REVISION", jefe);
        long ajena = crearPedido(LocalDateTime.of(2030, 1, 10, 18, 0), "EN_REVISION", asistente);

        String json = mockMvc.perform(get("/api/solicitudes/vencimiento").param("soloMias", "true")
                        .with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].solicitud.asignado.nombreUsuario").value(jefe))
                .andReturn().getResponse().getContentAsString();

        assertThat(json).contains("\"idSolicitud\":" + mia).doesNotContain("\"idSolicitud\":" + ajena + ",");
    }

    @Test
    void lista_elClienteNoLaVe() throws Exception {
        mockMvc.perform(get("/api/solicitudes/vencimiento").with(user(cliente).roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- alertas

    @Test
    void al75PorCiento_avisaUnaVezAlAsistenteAsignado() throws Exception {
        long id = crearPedidoConFechas("EN_REVISION", asistente);
        LocalDateTime martes15 = LocalDateTime.of(2026, 11, 10, 15, 0);

        alertas.revisarPlazos(martes15);
        alertas.revisarPlazos(martes15.plusMinutes(15));

        assertThat(avisos("PLAZO_POR_VENCER", id, asistente)).isEqualTo(1);
        assertThat(avisos("PLAZO_VENCIDO", id, jefe)).isZero();
    }

    @Test
    void sinAsignado_avisaATodosLosAsistentes() throws Exception {
        long id = crearPedidoConFechas("PENDIENTE", null);
        String otroAsistente = datos.nuevoInterno("ASISTENTE_LOGISTICO");

        alertas.revisarPlazos(LocalDateTime.of(2026, 11, 10, 15, 0));

        assertThat(avisos("PLAZO_POR_VENCER", id, asistente)).isEqualTo(1);
        assertThat(avisos("PLAZO_POR_VENCER", id, otroAsistente)).isEqualTo(1);
    }

    @Test
    void alVencer_avisaUnaVezAlJefeAdministrativo() throws Exception {
        long id = crearPedidoConFechas("EN_REVISION", asistente);
        LocalDateTime miercoles9 = LocalDateTime.of(2026, 11, 11, 9, 0);

        alertas.revisarPlazos(miercoles9);
        alertas.revisarPlazos(miercoles9.plusHours(1));

        assertThat(avisos("PLAZO_VENCIDO", id, jefe)).isEqualTo(1);
    }

    @Test
    void pausada_noGeneraAlertas() throws Exception {
        long id = crearPedidoConFechas("EN_ESPERA_TERCERO", asistente);

        alertas.revisarPlazos(LocalDateTime.of(2026, 11, 11, 9, 0));

        assertThat(avisos("PLAZO_VENCIDO", id, jefe)).isZero();
        assertThat(avisos("PLAZO_POR_VENCER", id, asistente)).isZero();
    }

    @Test
    void siCambiaElPlazo_vuelveAAvisar() throws Exception {
        long id = crearPedidoConFechas("EN_REVISION", asistente);
        alertas.revisarPlazos(LocalDateTime.of(2026, 11, 10, 15, 0));

        // Se reclasificó después del primer aviso: el plazo nuevo merece su propio aviso.
        jdbc.update("INSERT INTO solicitud_evento (id_solicitud, tipo_evento, detalle, fecha_evento) "
                + "VALUES (?, 'RECLASIFICA', 'COMUN → IMPORTADO', ?)", id, Timestamp.valueOf(LocalDateTime.now().plusSeconds(1)));
        alertas.revisarPlazos(LocalDateTime.of(2026, 11, 10, 15, 30));

        assertThat(avisos("PLAZO_POR_VENCER", id, asistente)).isEqualTo(2);
    }

    // ---------------------------------------------------------------- apoyo

    private long crearPedidoConFechas(String estado, String asignado) throws Exception {
        long id = crearPedido(LIMITE, estado, asignado);
        jdbc.update("UPDATE solicitud_cotizacion SET date_create = ? WHERE id_solicitud = ?", Timestamp.valueOf(RECIBIDA), id);
        em.clear();
        return id;
    }

    private long crearPedido(LocalDateTime limite, String estado, String asignado) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/solicitudes").with(user(cliente).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"canal":"PORTAL","asunto":"Filtros",
                                 "items":[{"descripcion":"Filtro","cantidad":1,"marcaTexto":"CAT","numeroParte":"1R-0750"}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(cuerpo.replaceAll(".*\"idSolicitud\":(\\d+).*", "$1"));
        jdbc.update("UPDATE solicitud_cotizacion SET fecha_limite_interna = ?, estado_solicitud = ?, id_usuario_asignado = ?, "
                        + "pausada_desde = ? WHERE id_solicitud = ?",
                Timestamp.valueOf(limite), estado, asignado == null ? null : datos.idUsuario(asignado),
                "EN_ESPERA_TERCERO".equals(estado) ? Timestamp.valueOf(RECIBIDA.plusHours(1)) : null, id);
        em.clear();
        return id;
    }

    private int avisos(String tipo, long idSolicitud, String usuario) {
        em.flush();
        return jdbc.queryForObject("SELECT COUNT(*) FROM notificacion WHERE tipo = ? AND enlace = ? AND id_usuario = ?",
                Integer.class, tipo, "/solicitudes/" + idSolicitud, datos.idUsuario(usuario));
    }
}
