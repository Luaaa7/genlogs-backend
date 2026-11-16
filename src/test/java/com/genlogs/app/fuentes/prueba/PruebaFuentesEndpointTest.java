package com.genlogs.app.fuentes.prueba;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.EstadoFuente;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfig;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfigRepository;
import com.genlogs.app.support.ClientesDePrueba;

import jakarta.persistence.EntityManager;

/**
 * M2 (v3.3, BPMN 9-C, D-37) · Prueba de 10 pedidos. Cada corrida queda en fuente_prueba y sus
 * aciertos en fuente_busqueda.aciertos_prueba; con menos de 4 aciertos «Activar» responde 409.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(ClientesDePrueba.class)
class PruebaFuentesEndpointTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ClientesDePrueba datos;
    @Autowired private FuenteBusquedaConfigRepository fuentes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

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

    // ---------------------------------------------------------------- apoyo

    /** Una prueba de 10 pedidos; cada tienda acierta en sus primeros N pedidos. */
    private static String corrida(int pedidos, Object... tiendaYAciertos) {
        List<String> lista = new ArrayList<>();
        for (int i = 0; i < pedidos; i++) {
            List<String> resultados = new ArrayList<>();
            for (int t = 0; t < tiendaYAciertos.length; t += 2) {
                String tienda = (String) tiendaYAciertos[t];
                int aciertos = (Integer) tiendaYAciertos[t + 1];
                boolean acerto = i < aciertos;
                resultados.add("{\"fuente\":\"" + tienda + "\",\"acerto\":" + acerto
                        + (acerto ? ",\"enlace\":\"https://www.ebay.com/itm/" + (100 + i) + "\"" : "") + "}");
            }
            lista.add("{\"pedido\":\"CAT 1R-07" + (50 + i) + "\",\"resultados\":[" + String.join(",", resultados) + "]}");
        }
        return "{\"pedidos\":[" + String.join(",", lista) + "]}";
    }

    private ResultActions guardar(String cuerpo) throws Exception {
        return mockMvc.perform(post("/api/fuentes/prueba/corridas").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private FuenteBusquedaConfig fuente(CodigoFuente codigo) {
        em.flush();
        em.clear();
        return fuentes.findByCodigo(codigo).orElseThrow();
    }

    private int filas(CodigoFuente codigo) {
        em.flush();
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM fuente_prueba p JOIN fuente_busqueda f "
                + "ON f.id_fuente = p.id_fuente WHERE f.codigo = ?", Integer.class, codigo.name());
        return n == null ? 0 : n;
    }

    // ---------------------------------------------------------------- guardar corrida

    @Test
    void corrida_guardaUnaFilaPorPedidoYTiendaYLosAciertos() throws Exception {
        guardar(corrida(10, "EBAY", 4, "AMAZON", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].codigo").value("EBAY"))
                .andExpect(jsonPath("$[0].corrida").value(1))
                .andExpect(jsonPath("$[0].aciertos").value(4))
                .andExpect(jsonPath("$[0].pedidos").value(10))
                .andExpect(jsonPath("$[0].aprobada").value(true))
                .andExpect(jsonPath("$[1].codigo").value("AMAZON"))
                .andExpect(jsonPath("$[1].aciertos").value(2))
                .andExpect(jsonPath("$[1].aprobada").value(false));

        assertThat(filas(CodigoFuente.EBAY)).isEqualTo(10);
        assertThat(filas(CodigoFuente.AMAZON)).isEqualTo(10);
        assertThat(filas(CodigoFuente.ALIBABA)).isZero();
        assertThat(fuente(CodigoFuente.EBAY).getAciertosPrueba()).isEqualTo((short) 4);
        assertThat(fuente(CodigoFuente.AMAZON).getAciertosPrueba()).isEqualTo((short) 2);
    }

    @Test
    void corrida_laSegundaEsLaDosYSusAciertosMandan() throws Exception {
        guardar(corrida(10, "EBAY", 2)).andExpect(status().isOk());
        guardar(corrida(10, "EBAY", 6))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].corrida").value(2))
                .andExpect(jsonPath("$[0].aciertos").value(6));

        assertThat(filas(CodigoFuente.EBAY)).isEqualTo(20);
        assertThat(fuente(CodigoFuente.EBAY).getAciertosPrueba()).isEqualTo((short) 6);
    }

    /** Criterio de M2: activar con 3 aciertos responde 409. */
    @Test
    void conTresAciertos_activarResponde409() throws Exception {
        FuenteBusquedaConfig ebay = fuentes.findByCodigo(CodigoFuente.EBAY).orElseThrow();
        ebay.setEstado(EstadoFuente.ACTIVA);
        fuentes.save(ebay);

        guardar(corrida(10, "EBAY", 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].aprobada").value(false));

        mockMvc.perform(patch("/api/fuentes/EBAY/activar").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activa\":true}"))
                .andExpect(status().isConflict());
        assertThat(fuente(CodigoFuente.EBAY).getActiva()).isFalse();
    }

    @Test
    void conCuatroAciertos_seActiva() throws Exception {
        FuenteBusquedaConfig ebay = fuentes.findByCodigo(CodigoFuente.EBAY).orElseThrow();
        ebay.setEstado(EstadoFuente.ACTIVA);
        fuentes.save(ebay);

        guardar(corrida(10, "EBAY", 4)).andExpect(status().isOk());

        mockMvc.perform(patch("/api/fuentes/EBAY/activar").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activa\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true));
    }

    @Test
    void siEstabaActivaYLaNuevaCorridaNoLlega_seApaga() throws Exception {
        FuenteBusquedaConfig ebay = fuentes.findByCodigo(CodigoFuente.EBAY).orElseThrow();
        ebay.setEstado(EstadoFuente.ACTIVA);
        ebay.setActiva(true);
        ebay.setAciertosPrueba((short) 7);
        fuentes.save(ebay);

        guardar(corrida(10, "EBAY", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].desactivada").value(true));

        FuenteBusquedaConfig despues = fuente(CodigoFuente.EBAY);
        assertThat(despues.getActiva()).isFalse();
        assertThat(despues.getAciertosPrueba()).isEqualTo((short) 2);
    }

    @Test
    void conMenosDeDiezPedidos_responde400YNoGuardaNada() throws Exception {
        guardar(corrida(9, "EBAY", 9)).andExpect(status().isBadRequest());
        assertThat(filas(CodigoFuente.EBAY)).isZero();
    }

    @Test
    void conUnPedidoRepetido_responde400() throws Exception {
        String repetido = corrida(10, "EBAY", 5).replace("CAT 1R-0751", "CAT 1R-0750");
        guardar(repetido).andExpect(status().isBadRequest());
        assertThat(filas(CodigoFuente.EBAY)).isZero();
    }

    @Test
    void unaTiendaSinMarcarEnTodosLosPedidos_responde400() throws Exception {
        String incompleta = corrida(10, "EBAY", 5, "AMAZON", 5)
                .replaceFirst(",\\{\"fuente\":\"AMAZON\",\"acerto\":true,\"enlace\":\"https://www.ebay.com/itm/100\"\\}", "");
        guardar(incompleta).andExpect(status().isBadRequest());
        assertThat(filas(CodigoFuente.EBAY)).isZero();
    }

    @Test
    void unaTiendaQueNoExiste_responde404() throws Exception {
        guardar(corrida(10, "TEMU", 5)).andExpect(status().isNotFound());
    }

    @Test
    void soloElJefe() throws Exception {
        mockMvc.perform(post("/api/fuentes/prueba/corridas").with(user(asistente).roles("ASISTENTE_LOGISTICO"))
                        .contentType(MediaType.APPLICATION_JSON).content(corrida(10, "EBAY", 5)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/fuentes/prueba").with(user(asistente).roles("ASISTENTE_LOGISTICO")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/fuentes/prueba/buscar").with(user(asistente).roles("ASISTENTE_LOGISTICO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"numeroParte\":\"1R-0750\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- resumen

    @Test
    void resumen_muestraLaUltimaCorridaDeCadaTienda() throws Exception {
        guardar(corrida(10, "EBAY", 2)).andExpect(status().isOk());
        guardar(corrida(10, "EBAY", 5)).andExpect(status().isOk());
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/fuentes/prueba").with(user(jefe).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].codigo").value("MERCADO_LIBRE"))
                .andExpect(jsonPath("$[0].corrida").value(0))
                .andExpect(jsonPath("$[0].detalle.length()").value(0))
                .andExpect(jsonPath("$[1].codigo").value("EBAY"))
                .andExpect(jsonPath("$[1].corrida").value(2))
                .andExpect(jsonPath("$[1].aciertos").value(5))
                .andExpect(jsonPath("$[1].aprobada").value(true))
                .andExpect(jsonPath("$[1].detalle.length()").value(10))
                .andExpect(jsonPath("$[1].detalle[0].pedido").value("CAT 1R-0750"))
                .andExpect(jsonPath("$[1].detalle[0].acerto").value(true))
                .andExpect(jsonPath("$[1].detalle[9].acerto").value(false));
    }

    // ---------------------------------------------------------------- buscar

    @Test
    void buscar_sinSesionProbadaOmiteLaTiendaSinConsultarla() throws Exception {
        mockMvc.perform(post("/api/fuentes/prueba/buscar").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marca\":\"CAT\",\"numeroParte\":\"1R-0750\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[1].codigo").value("EBAY"))
                .andExpect(jsonPath("$[1].estado").value("OMITIDA_SESION"));
        assertThat(filas(CodigoFuente.EBAY)).isZero();
    }

    @Test
    void buscar_sinNumeroDeParteResponde400() throws Exception {
        mockMvc.perform(post("/api/fuentes/prueba/buscar").with(user(jefe).roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"marca\":\"CAT\"}"))
                .andExpect(status().isBadRequest());
    }
}