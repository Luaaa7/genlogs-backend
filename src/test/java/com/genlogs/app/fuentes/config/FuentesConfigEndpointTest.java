package com.genlogs.app.fuentes.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.support.ClientesDePrueba;

import jakarta.persistence.EntityManager;

/**
 * L5 (v3.3, RF-88 y RF-89) · Endpoints de Configuración > Fuentes de búsqueda.
 * Solo el Jefe Administrativo configura; el Asistente Logístico solo ve el estado.
 * Ninguna respuesta trae usuario, clave ni sesión de una tienda.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(ClientesDePrueba.class)
class FuentesConfigEndpointTest {

    private static final String USUARIO_TIENDA = "compras.genlogs@ejemplo.pe";
    private static final String CLAVE_TIENDA = "Clave-Secreta-123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ClientesDePrueba datos;
    @Autowired private FuenteBusquedaConfigRepository fuentes;
    @Autowired private CredencialesFuenteService credenciales;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    private String jefe;
    private String asistente;
    private String contador;

    @BeforeEach
    void preparar() {
        jefe = datos.nuevoInterno("JEFE_ADMINISTRATIVO");
        asistente = datos.nuevoInterno("ASISTENTE_LOGISTICO");
        contador = datos.nuevoInterno("CONTADOR_GENERAL");
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

    // ---------------------------------------------------------------- listar

    @Test
    void listar_jefeVeLasCincoFuentesConSuEstado() throws Exception {
        mockMvc.perform(get("/api/fuentes").with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].codigo").value("MERCADO_LIBRE"))
                .andExpect(jsonPath("$[0].estado").value("SIN_PROBAR"))
                .andExpect(jsonPath("$[0].activa").value(false))
                .andExpect(jsonPath("$[0].tieneUsuario").value(false))
                .andExpect(jsonPath("$[0].pausaSegundos").value(5))
                .andExpect(jsonPath("$[0].limiteDiario").value(50));
    }

    @Test
    void listar_asistenteLogisticoVeElEstado() throws Exception {
        mockMvc.perform(get("/api/fuentes").with(user(asistente).roles("ASISTENTE_LOGISTICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void listar_otroRolNoEntra() throws Exception {
        mockMvc.perform(get("/api/fuentes").with(user(contador).roles("CONTADOR_GENERAL")))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- credenciales

    @Test
    void credenciales_seGuardanCifradasYNoVuelvenEnNingunaRespuesta() throws Exception {
        mockMvc.perform(put("/api/fuentes/EBAY/credenciales").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + USUARIO_TIENDA + "\",\"clave\":\"" + CLAVE_TIENDA + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(credenciales.leer(CodigoFuente.EBAY, TipoCredencial.CLAVE)).contains(CLAVE_TIENDA);
        String enBase = jdbc.queryForObject(
                "SELECT valor_cifrado FROM fuente_credencial c JOIN fuente_busqueda f ON f.id_fuente = c.id_fuente "
                        + "WHERE f.codigo = 'EBAY' AND c.tipo = 'CLAVE'", String.class);
        assertThat(enBase).doesNotContain(CLAVE_TIENDA);

        MvcResult lista = mockMvc.perform(get("/api/fuentes").with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].codigo").value("EBAY"))
                .andExpect(jsonPath("$[1].tieneUsuario").value(true))
                .andExpect(jsonPath("$[1].tieneClave").value(true))
                .andReturn();
        String cuerpo = lista.getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain(CLAVE_TIENDA).doesNotContain(USUARIO_TIENDA).doesNotContain(enBase);
    }

    @Test
    void credenciales_cambiarlasDejaLaFuenteSinProbarYSinSesion() throws Exception {
        FuenteBusquedaConfig ebay = fuentes.findByCodigo(CodigoFuente.EBAY).orElseThrow();
        ebay.setEstado(EstadoFuente.ACTIVA);
        ebay.setSesionValidaHasta(LocalDateTime.now().plusDays(3));
        fuentes.save(ebay);
        credenciales.guardar(CodigoFuente.EBAY, TipoCredencial.SESION, "cookie=abc", null, "PRUEBA");
        em.flush();

        mockMvc.perform(put("/api/fuentes/EBAY/credenciales").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clave\":\"OtraClave-456\"}"))
                .andExpect(status().isNoContent());
        em.flush();
        em.clear();

        FuenteBusquedaConfig despues = fuentes.findByCodigo(CodigoFuente.EBAY).orElseThrow();
        assertThat(despues.getEstado()).isEqualTo(EstadoFuente.SIN_PROBAR);
        assertThat(despues.getSesionValidaHasta()).isNull();
        assertThat(credenciales.tiene(CodigoFuente.EBAY, TipoCredencial.SESION)).isFalse();
    }

    @Test
    void credenciales_sinNingunDatoResponde400() throws Exception {
        mockMvc.perform(put("/api/fuentes/EBAY/credenciales").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void credenciales_soloElJefe() throws Exception {
        mockMvc.perform(put("/api/fuentes/EBAY/credenciales").with(user(asistente).roles("ASISTENTE_LOGISTICO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"clave\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void fuenteQueNoExisteResponde404() throws Exception {
        mockMvc.perform(put("/api/fuentes/TEMU/credenciales").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"clave\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- activar

    @Test
    void activar_sinPruebaDeDiezAprobadaResponde409() throws Exception {
        FuenteBusquedaConfig amazon = fuentes.findByCodigo(CodigoFuente.AMAZON).orElseThrow();
        amazon.setEstado(EstadoFuente.ACTIVA);
        amazon.setAciertosPrueba((short) 3);
        fuentes.save(amazon);

        mockMvc.perform(patch("/api/fuentes/AMAZON/activar").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activa\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void activar_sinSesionValidaResponde409() throws Exception {
        FuenteBusquedaConfig amazon = fuentes.findByCodigo(CodigoFuente.AMAZON).orElseThrow();
        amazon.setEstado(EstadoFuente.CON_ERROR);
        amazon.setAciertosPrueba((short) 7);
        fuentes.save(amazon);

        mockMvc.perform(patch("/api/fuentes/AMAZON/activar").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activa\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void activar_conSesionYCuatroAciertosQuedaActiva() throws Exception {
        FuenteBusquedaConfig amazon = fuentes.findByCodigo(CodigoFuente.AMAZON).orElseThrow();
        amazon.setEstado(EstadoFuente.ACTIVA);
        amazon.setAciertosPrueba((short) 4);
        fuentes.save(amazon);

        mockMvc.perform(patch("/api/fuentes/AMAZON/activar").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activa\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true));
    }

    @Test
    void desactivar_siempreSePuede() throws Exception {
        FuenteBusquedaConfig amazon = fuentes.findByCodigo(CodigoFuente.AMAZON).orElseThrow();
        amazon.setActiva(true);
        amazon.setEstado(EstadoFuente.CON_ERROR);
        fuentes.save(amazon);

        mockMvc.perform(patch("/api/fuentes/AMAZON/activar").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activa\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));
    }

    // ---------------------------------------------------------------- parámetros

    @Test
    void parametros_cambiaPausaYLimiteDiario() throws Exception {
        mockMvc.perform(patch("/api/fuentes/ALIBABA/parametros").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pausaSegundos\":8,\"limiteDiario\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pausaSegundos").value(8))
                .andExpect(jsonPath("$.limiteDiario").value(30));
    }

    @Test
    void parametros_limiteCeroOPausaNegativaResponde400() throws Exception {
        mockMvc.perform(patch("/api/fuentes/ALIBABA/parametros").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pausaSegundos\":-1,\"limiteDiario\":0}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- reglas de extracción

    @Test
    void selectores_seGuardanYSeLeen() throws Exception {
        mockMvc.perform(put("/api/fuentes/EBAY/selectores").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"pagina\":\"FICHA\",\"campo\":\"PRECIO\",\"regla\":\".x-price-primary span\"},"
                                + "{\"pagina\":\"FICHA\",\"campo\":\"NOMBRE\",\"regla\":\"h1.x-item-title\"}]"))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/fuentes/EBAY/selectores").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"pagina\":\"FICHA\",\"campo\":\"PRECIO\",\"regla\":\"#prcIsum\"}]"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/fuentes/EBAY/selectores").with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.campo=='PRECIO')].regla").value("#prcIsum"));
    }

    @Test
    void selectores_reglaVaciaResponde400() throws Exception {
        mockMvc.perform(put("/api/fuentes/EBAY/selectores").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"pagina\":\"FICHA\",\"campo\":\"PRECIO\",\"regla\":\" \"}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void selectores_soloElJefe() throws Exception {
        mockMvc.perform(get("/api/fuentes/EBAY/selectores").with(user(asistente).roles("ASISTENTE_LOGISTICO")))
                .andExpect(status().isForbidden());
    }
}
