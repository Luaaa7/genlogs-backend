package com.genlogs.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.model.Auditable;
import com.genlogs.app.model.ParametroSistema;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.ParametroSistemaRepository;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.ClavesParametro;
import com.genlogs.app.service.ParametrosService;

/**
 * Validaciones de la edición de parámetros que complementan a {@link EdicionParametrosTest}:
 * negativos, valores no numéricos, cambio de los tres márgenes juntos y permisos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EdicionParametrosValidacionTest {

    private static final String JEFE = "jefe.validacion";
    private static final String MARGENES = "/api/parametros/margenes-repuesto";

    @Autowired private MockMvc mockMvc;
    @Autowired private ParametrosService parametrosService;
    @Autowired private ParametroSistemaRepository parametros;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;

    private Long idJefe;

    @BeforeEach
    void datos() {
        Rol rol = roles.save(auditar(new Rol(), r -> {
            r.setNombreRol("ROL_VALIDACION_PARAM");
            r.setDescripcion("Prueba");
        }));
        idJefe = usuarios.save(auditar(new Usuario(), u -> {
            u.setRol(rol);
            u.setNombreUsuario(JEFE);
            u.setNombres("Jefe Validacion");
            u.setCorreo("jefe.validacion@genlogs.test");
            u.setPasswordHash("{noop}demo");
            u.setIniciales("JV");
            u.setBloqueado(false);
            u.setIntentosFallidos((short) 0);
        })).getIdUsuario();
        parametro(ClavesParametro.MONTO_ALTO_VALOR_PEN, "20000");
        parametro(ClavesParametro.PLAZO_COMUN_DIAS, "1");
        parametro(ClavesParametro.MARGEN_REP_MIN, "15");
        parametro(ClavesParametro.MARGEN_REP_DEFAULT, "35");
        parametro(ClavesParametro.MARGEN_REP_MAX, "45");
        parametro(ClavesParametro.MARGEN_SERV_MIN, "15");
        parametro(ClavesParametro.MARGEN_SERV_SUGERIDO, "25");
        parametro(ClavesParametro.MARGEN_SERV_AVISO, "35");
        parametro(ClavesParametro.MARGEN_SERV_MAX, "45");
    }

    // ---- Valores iniciales ----

    @Test
    void laMigracionBeDCargaLosMargenesIniciales() throws Exception {
        String sql = Files.readString(Path.of("db/migraciones/v3_2_be_d.sql"), StandardCharsets.UTF_8);
        assertTrue(Pattern.compile("\\('MARGEN_REP_MIN',\\s*'15'").matcher(sql).find(), "MARGEN_REP_MIN = 15");
        assertTrue(Pattern.compile("\\('MARGEN_REP_DEFAULT',\\s*'35'").matcher(sql).find(), "MARGEN_REP_DEFAULT = 35");
        assertTrue(Pattern.compile("\\('MARGEN_REP_MAX',\\s*'45'").matcher(sql).find(), "MARGEN_REP_MAX = 45");
        assertTrue(sql.contains("ON CONFLICT (clave) DO NOTHING"), "el INSERT debe ser idempotente");
    }

    @Test
    void elListadoMuestraLosMargenesIniciales() throws Exception {
        // GERENTE_GENERAL es interno con o sin la corrección de ROLES_INTERNOS de BE-A.
        mockMvc.perform(get("/api/parametros").with(user("gerente").roles("GERENTE_GENERAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.clave=='MARGEN_REP_MIN')].valor").value("15"))
                .andExpect(jsonPath("$[?(@.clave=='MARGEN_REP_DEFAULT')].valor").value("35"))
                .andExpect(jsonPath("$[?(@.clave=='MARGEN_REP_MAX')].valor").value("45"));
    }

    // ---- Edición de a una clave ----

    @Test
    void unMargenNegativoSeRechaza() throws Exception {
        putClave(ClavesParametro.MARGEN_REP_MIN, "-5").andExpect(status().isBadRequest());
        assertEquals("15", parametrosService.get(ClavesParametro.MARGEN_REP_MIN));
    }

    @Test
    void unMargenQueNoEsNumeroSeRechaza() throws Exception {
        putClave(ClavesParametro.MARGEN_REP_DEFAULT, "treinta").andExpect(status().isBadRequest());
        putClave(ClavesParametro.MARGEN_REP_DEFAULT, "3e1").andExpect(status().isBadRequest());
        assertEquals("35", parametrosService.get(ClavesParametro.MARGEN_REP_DEFAULT));
    }

    @Test
    void unMargenValidoDentroDelRangoSeGuarda() throws Exception {
        putClave(ClavesParametro.MARGEN_REP_DEFAULT, "30.5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value("30.5"));
        assertEquals("30.5", parametrosService.get(ClavesParametro.MARGEN_REP_DEFAULT));
    }

    @Test
    void elDefectoPorEncimaDelMaximoSeRechaza() throws Exception {
        putClave(ClavesParametro.MARGEN_REP_DEFAULT, "50").andExpect(status().isBadRequest());
        assertEquals("35", parametrosService.get(ClavesParametro.MARGEN_REP_DEFAULT));
    }

    @Test
    void unPlazoNegativoDecimalOTextoSeRechaza() throws Exception {
        putClave(ClavesParametro.PLAZO_COMUN_DIAS, "-3").andExpect(status().isBadRequest());
        putClave(ClavesParametro.PLAZO_COMUN_DIAS, "2.5").andExpect(status().isBadRequest());
        putClave(ClavesParametro.PLAZO_COMUN_DIAS, "abc").andExpect(status().isBadRequest());
        assertEquals(1, parametrosService.getEntero(ClavesParametro.PLAZO_COMUN_DIAS));
    }

    @Test
    void unPlazoEnteroSeGuardaYSeLeeComoEntero() throws Exception {
        putClave(ClavesParametro.PLAZO_COMUN_DIAS, "2").andExpect(status().isOk());
        assertEquals(2, parametrosService.getEntero(ClavesParametro.PLAZO_COMUN_DIAS));
    }

    @Test
    void unMontoNegativoSeRechaza() throws Exception {
        putClave(ClavesParametro.MONTO_ALTO_VALOR_PEN, "-100").andExpect(status().isBadRequest());
        assertEquals("20000", parametrosService.get(ClavesParametro.MONTO_ALTO_VALOR_PEN));
    }

    // ---- Los tres márgenes juntos ----

    @Test
    void elJefeMueveTodoElRangoEnUnaSolaOperacion() throws Exception {
        // De 15/35/45 a 50/60/70: de a uno no se podría empezar por MIN ni por DEFAULT.
        putMargenes("{\"min\":50,\"porDefecto\":60,\"max\":70}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].clave").value(ClavesParametro.MARGEN_REP_MIN))
                .andExpect(jsonPath("$[0].valor").value("50"))
                .andExpect(jsonPath("$[1].valor").value("60"))
                .andExpect(jsonPath("$[2].valor").value("70"));

        assertEquals("50", parametrosService.get(ClavesParametro.MARGEN_REP_MIN));
        assertEquals("60", parametrosService.get(ClavesParametro.MARGEN_REP_DEFAULT));
        assertEquals("70", parametrosService.get(ClavesParametro.MARGEN_REP_MAX));
        assertEquals(idJefe, parametros.findById(ClavesParametro.MARGEN_REP_MAX).orElseThrow().getEditadoPor());
    }

    @Test
    void siLosTresNoQuedanOrdenadosNoSeGuardaNinguno() throws Exception {
        putMargenes("{\"min\":50,\"porDefecto\":40,\"max\":70}").andExpect(status().isBadRequest());
        assertMargenesIniciales();
    }

    @Test
    void unMargenNegativoEnElCambioConjuntoSeRechaza() throws Exception {
        putMargenes("{\"min\":-1,\"porDefecto\":35,\"max\":45}").andExpect(status().isBadRequest());
        assertMargenesIniciales();
    }

    @Test
    void faltaUnMargenEnElCambioConjunto() throws Exception {
        putMargenes("{\"min\":10,\"max\":45}").andExpect(status().isBadRequest());
        assertMargenesIniciales();
    }

    @Test
    void unMargenQueNoEsNumeroEnElCambioConjunto() throws Exception {
        putMargenes("{\"min\":\"diez\",\"porDefecto\":35,\"max\":45}").andExpect(status().isBadRequest());
        assertMargenesIniciales();
    }

    // ---- Márgenes de servicios (MIN <= SUGERIDO <= AVISO <= MAX) ----

    @Test
    void elSugeridoDeServiciosNoPuedePasarDelAviso() throws Exception {
        putClave(ClavesParametro.MARGEN_SERV_SUGERIDO, "40").andExpect(status().isBadRequest());
        assertEquals("25", parametrosService.get(ClavesParametro.MARGEN_SERV_SUGERIDO));
    }

    @Test
    void elMaximoDeServiciosNoPuedeQuedarBajoElAviso() throws Exception {
        putClave(ClavesParametro.MARGEN_SERV_MAX, "30").andExpect(status().isBadRequest());
        assertEquals("45", parametrosService.get(ClavesParametro.MARGEN_SERV_MAX));
    }

    @Test
    void unMargenDeServiciosEnOrdenSeGuarda() throws Exception {
        putClave(ClavesParametro.MARGEN_SERV_MIN, "20").andExpect(status().isOk());
        putClave(ClavesParametro.MARGEN_SERV_MAX, "50").andExpect(status().isOk());
        assertEquals("20", parametrosService.get(ClavesParametro.MARGEN_SERV_MIN));
        assertEquals("50", parametrosService.get(ClavesParametro.MARGEN_SERV_MAX));
    }

    @Test
    void unMargenDeServiciosNegativoSeRechaza() throws Exception {
        putClave(ClavesParametro.MARGEN_SERV_MIN, "-1").andExpect(status().isBadRequest());
        assertEquals("15", parametrosService.get(ClavesParametro.MARGEN_SERV_MIN));
    }

    // ---- Permisos ----

    @Test
    void otrosRolesNoCambianLosTresMargenes() throws Exception {
        // GERENTE_GENERAL y CONTADOR_GENERAL pasan el filtro de /api/parametros/** y los frena el
        // @PreAuthorize; CLIENTE y ASISTENTE_LOGISTICO reciben 403 antes o después del cambio de BE-A.
        for (String rol : new String[] {"GERENTE_GENERAL", "CONTADOR_GENERAL", "ASISTENTE_LOGISTICO", "CLIENTE"}) {
            mockMvc.perform(put(MARGENES)
                            .with(user("otro").roles(rol))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"min\":10,\"porDefecto\":20,\"max\":30}"))
                    .andExpect(status().isForbidden());
        }
        assertMargenesIniciales();
    }

    @Test
    void otrosRolesNoCambianUnMargen() throws Exception {
        mockMvc.perform(put("/api/parametros/" + ClavesParametro.MARGEN_REP_MIN)
                        .with(user("contador").roles("CONTADOR_GENERAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"10\"}"))
                .andExpect(status().isForbidden());
        assertMargenesIniciales();
    }

    @Test
    void sinSesionNoSeCambianLosMargenes() throws Exception {
        mockMvc.perform(put(MARGENES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"min\":10,\"porDefecto\":20,\"max\":30}"))
                .andExpect(status().isUnauthorized());
        assertMargenesIniciales();
    }

    // ---- Apoyo ----

    private org.springframework.test.web.servlet.ResultActions putClave(String clave, String valor) throws Exception {
        return mockMvc.perform(put("/api/parametros/" + clave)
                .with(jefe())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\":\"" + valor + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions putMargenes(String json) throws Exception {
        return mockMvc.perform(put(MARGENES)
                .with(jefe())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void assertMargenesIniciales() {
        assertEquals("15", parametrosService.get(ClavesParametro.MARGEN_REP_MIN));
        assertEquals("35", parametrosService.get(ClavesParametro.MARGEN_REP_DEFAULT));
        assertEquals("45", parametrosService.get(ClavesParametro.MARGEN_REP_MAX));
    }

    private UserRequestPostProcessor jefe() {
        return user(JEFE).roles("JEFE_ADMINISTRATIVO");
    }

    private void parametro(String clave, String valor) {
        ParametroSistema parametro = new ParametroSistema();
        parametro.setClave(clave);
        parametro.setValor(valor);
        parametro.setDescripcion("Prueba");
        parametro.setEditadoEn(LocalDateTime.now());
        parametros.saveAndFlush(parametro);
    }

    private static <T extends Auditable> T auditar(T entidad, Consumer<T> completar) {
        completar.accept(entidad);
        entidad.setUserCreate("test");
        entidad.setProcessCreate("TEST");
        entidad.setDateCreate(LocalDateTime.now());
        entidad.setStatus("A");
        return entidad;
    }
}
