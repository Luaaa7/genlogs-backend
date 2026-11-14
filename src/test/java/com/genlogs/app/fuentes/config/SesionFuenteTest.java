package com.genlogs.app.fuentes.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.support.ClientesDePrueba;

import jakarta.persistence.EntityManager;

/**
 * L1 (v3.3, BPMN 9-A) · «Probar sesión»: el servicio de lectura inicia sesión en la tienda con la
 * cuenta de búsqueda (D-38) y la fuente queda ACTIVA, REQUIERE_VERIFICACION o CON_ERROR.
 * La sesión se guarda cifrada hasta sesion_valida_hasta y se renueva sola antes de buscar.
 * El servicio de lectura va simulado: la integración real con P2 es otra tarea.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(ClientesDePrueba.class)
class SesionFuenteTest {

    private static final String USUARIO_TIENDA = "busqueda.genlogs@ejemplo.pe";
    private static final String CLAVE_TIENDA = "Clave-Secreta-123";
    private static final String SESION = "{\"cookies\":[{\"name\":\"sid\",\"value\":\"cookie-muy-secreta\"}]}";

    @Autowired private MockMvc mockMvc;
    @Autowired private ClientesDePrueba datos;
    @Autowired private FuenteBusquedaConfigRepository fuentes;
    @Autowired private CredencialesFuenteService credenciales;
    @Autowired private SesionFuenteService sesiones;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    @MockitoBean private LectorSesion lector;

    private String jefe;
    private String asistente;

    @BeforeEach
    void preparar() {
        jefe = datos.nuevoInterno("JEFE_ADMINISTRATIVO");
        asistente = datos.nuevoInterno("ASISTENTE_LOGISTICO");
        for (CodigoFuente codigo : CodigoFuente.values()) {
            if (fuentes.findByCodigo(codigo).isEmpty()) {
                FuenteBusquedaConfig f = new FuenteBusquedaConfig();
                f.setCodigo(codigo);
                f.setNombre(codigo.name());
                f.setUrlBase("https://" + codigo.name().toLowerCase() + ".example");
                f.setUserCreate("PRUEBA");
                f.setProcessCreate("PRUEBA");
                fuentes.save(f);
            }
        }
        em.flush();
    }

    private void cargarCuenta(CodigoFuente codigo) {
        credenciales.guardar(codigo, TipoCredencial.USUARIO, USUARIO_TIENDA, null, "PRUEBA");
        credenciales.guardar(codigo, TipoCredencial.CLAVE, CLAVE_TIENDA, null, "PRUEBA");
    }

    private FuenteBusquedaConfig fuente(CodigoFuente codigo) {
        em.flush();
        em.clear();
        return fuentes.findByCodigo(codigo).orElseThrow();
    }

    // ---------------------------------------------------------------- probar sesión

    @Test
    void probar_sinCuentaGuardadaPideCargarlaPrimero() throws Exception {
        mockMvc.perform(post("/api/fuentes/EBAY/probar").with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cuenta")));

        verify(lector, never()).iniciarSesion(any(), any(), any(), any());
        assertThat(fuente(CodigoFuente.EBAY).getEstado()).isEqualTo(EstadoFuente.SIN_PROBAR);
    }

    @Test
    void probar_sesionIniciadaDejaLaFuenteActivaConSuSesionCifrada() throws Exception {
        cargarCuenta(CodigoFuente.EBAY);
        LocalDateTime hasta = LocalDateTime.now().plusDays(7).withNano(0);
        when(lector.iniciarSesion(CodigoFuente.EBAY, "https://ebay.example", USUARIO_TIENDA, CLAVE_TIENDA))
                .thenReturn(ResultadoSesion.ok(SESION, hasta));

        MvcResult r = mockMvc.perform(post("/api/fuentes/EBAY/probar").with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.ultimoError").doesNotExist())
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("cookie-muy-secreta", CLAVE_TIENDA);

        FuenteBusquedaConfig f = fuente(CodigoFuente.EBAY);
        assertThat(f.getEstado()).isEqualTo(EstadoFuente.ACTIVA);
        assertThat(f.getSesionValidaHasta()).isEqualTo(hasta);
        assertThat(f.getUltimaPrueba()).isNotNull();
        assertThat(f.getActiva()).isFalse(); // probar la sesión no enciende la fuente (D-37)
        assertThat(credenciales.leer(CodigoFuente.EBAY, TipoCredencial.SESION)).contains(SESION);
        String enBase = jdbc.queryForObject(
                "SELECT valor_cifrado FROM fuente_credencial c JOIN fuente_busqueda f ON f.id_fuente = c.id_fuente "
                        + "WHERE f.codigo = 'EBAY' AND c.tipo = 'SESION'", String.class);
        assertThat(enBase).doesNotContain("cookie-muy-secreta");
    }

    @Test
    void probar_tiendaPideCodigoDejaRequiereVerificacion() throws Exception {
        cargarCuenta(CodigoFuente.AMAZON);
        when(lector.iniciarSesion(any(), any(), any(), any()))
                .thenReturn(ResultadoSesion.verificacion("La tienda pidió un código de verificación."));

        mockMvc.perform(post("/api/fuentes/AMAZON/probar").with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REQUIERE_VERIFICACION"))
                .andExpect(jsonPath("$.ultimoError").value("La tienda pidió un código de verificación."));

        FuenteBusquedaConfig f = fuente(CodigoFuente.AMAZON);
        assertThat(f.getSesionValidaHasta()).isNull();
        assertThat(credenciales.tiene(CodigoFuente.AMAZON, TipoCredencial.SESION)).isFalse();
    }

    @Test
    void probar_errorDejaConErrorYBorraLaSesionAnterior() throws Exception {
        cargarCuenta(CodigoFuente.ALIBABA);
        credenciales.guardar(CodigoFuente.ALIBABA, TipoCredencial.SESION, SESION, null, "PRUEBA");
        when(lector.iniciarSesion(any(), any(), any(), any()))
                .thenReturn(ResultadoSesion.error("La tienda bloqueó la lectura."));

        mockMvc.perform(post("/api/fuentes/ALIBABA/probar").with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CON_ERROR"))
                .andExpect(jsonPath("$.ultimoError").value("La tienda bloqueó la lectura."));

        FuenteBusquedaConfig f = fuente(CodigoFuente.ALIBABA);
        assertThat(f.getSesionValidaHasta()).isNull();
        assertThat(credenciales.tiene(CodigoFuente.ALIBABA, TipoCredencial.SESION)).isFalse();
    }

    @Test
    void probar_soloElJefeAdministrativo() throws Exception {
        cargarCuenta(CodigoFuente.EBAY);
        mockMvc.perform(post("/api/fuentes/EBAY/probar").with(user(asistente).roles("ASISTENTE_LOGISTICO")))
                .andExpect(status().isForbidden());
        verify(lector, never()).iniciarSesion(any(), any(), any(), any());
    }

    @Test
    void probar_fuenteQueNoExisteResponde404() throws Exception {
        mockMvc.perform(post("/api/fuentes/TIENDA_X/probar").with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- sesión vigente (antes de buscar)

    @Test
    void sesionVigente_conSesionAlDiaLaDevuelveSinLlamarAlLector() {
        cargarCuenta(CodigoFuente.EBAY);
        credenciales.guardar(CodigoFuente.EBAY, TipoCredencial.SESION, SESION, null, "PRUEBA");
        FuenteBusquedaConfig f = fuentes.findByCodigo(CodigoFuente.EBAY).orElseThrow();
        f.setEstado(EstadoFuente.ACTIVA);
        f.setSesionValidaHasta(LocalDateTime.now().plusDays(3));

        assertThat(sesiones.sesionVigente(CodigoFuente.EBAY)).contains(SESION);
        verify(lector, never()).iniciarSesion(any(), any(), any(), any());
    }

    @Test
    void sesionVigente_vencidaSeRenuevaSolaAntesDeBuscar() {
        cargarCuenta(CodigoFuente.EBAY);
        credenciales.guardar(CodigoFuente.EBAY, TipoCredencial.SESION, "{\"vieja\":true}", null, "PRUEBA");
        FuenteBusquedaConfig f = fuentes.findByCodigo(CodigoFuente.EBAY).orElseThrow();
        f.setEstado(EstadoFuente.ACTIVA);
        f.setSesionValidaHasta(LocalDateTime.now().minusMinutes(1));
        LocalDateTime hasta = LocalDateTime.now().plusDays(7).withNano(0);
        when(lector.iniciarSesion(CodigoFuente.EBAY, "https://ebay.example", USUARIO_TIENDA, CLAVE_TIENDA))
                .thenReturn(ResultadoSesion.ok(SESION, hasta));

        assertThat(sesiones.sesionVigente(CodigoFuente.EBAY)).contains(SESION);
        FuenteBusquedaConfig despues = fuente(CodigoFuente.EBAY);
        assertThat(despues.getEstado()).isEqualTo(EstadoFuente.ACTIVA);
        assertThat(despues.getSesionValidaHasta()).isEqualTo(hasta);
    }

    @Test
    void sesionVigente_siLaRenovacionFallaQuedaFueraDeLaBusqueda() {
        cargarCuenta(CodigoFuente.EBAY);
        FuenteBusquedaConfig f = fuentes.findByCodigo(CodigoFuente.EBAY).orElseThrow();
        f.setEstado(EstadoFuente.ACTIVA);
        f.setSesionValidaHasta(LocalDateTime.now().minusMinutes(1));
        when(lector.iniciarSesion(any(), any(), any(), any()))
                .thenReturn(ResultadoSesion.error("El servicio de lectura no responde."));

        assertThat(sesiones.sesionVigente(CodigoFuente.EBAY)).isEmpty();
        assertThat(fuente(CodigoFuente.EBAY).getEstado()).isEqualTo(EstadoFuente.CON_ERROR);
    }

    @Test
    void sesionVigente_fuenteNoProbadaNoEntraNiLlamaAlLector() {
        cargarCuenta(CodigoFuente.EBAY);
        assertThat(sesiones.sesionVigente(CodigoFuente.EBAY)).isEmpty();
        verify(lector, never()).iniciarSesion(any(), any(), any(), any());
    }

    // ---------------------------------------------------------------- sin servicio de lectura

    @Test
    void sinServicioDeLecturaConectadoLaPruebaDaUnErrorClaro() {
        ResultadoSesion r = new LectorSesionNoConectado().iniciarSesion(CodigoFuente.EBAY, "https://ebay.example",
                USUARIO_TIENDA, CLAVE_TIENDA);
        assertThat(r.resultado()).isEqualTo(ResultadoSesion.Resultado.ERROR);
        assertThat(r.mensaje()).contains("servicio de lectura");
        assertThat(r.toString()).doesNotContain(CLAVE_TIENDA);
    }
}
