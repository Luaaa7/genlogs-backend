package com.genlogs.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.function.Consumer;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EdicionParametrosTest {

    private static final String JEFE = "jefe.parametros";

    @Autowired private MockMvc mockMvc;
    @Autowired private ParametrosService parametrosService;
    @Autowired private ParametroSistemaRepository parametros;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;

    private Long idJefe;

    @BeforeEach
    void datos() {
        Rol rol = roles.save(auditar(new Rol(), r -> {
            r.setNombreRol("ROL_EDICION_PARAM");
            r.setDescripcion("Prueba");
        }));
        idJefe = usuarios.save(auditar(new Usuario(), u -> {
            u.setRol(rol);
            u.setNombreUsuario(JEFE);
            u.setNombres("Jefe Parametros");
            u.setCorreo("jefe.parametros@genlogs.test");
            u.setPasswordHash("{noop}demo");
            u.setIniciales("JP");
            u.setBloqueado(false);
            u.setIntentosFallidos((short) 0);
        })).getIdUsuario();
        parametro(ClavesParametro.MONTO_ALTO_VALOR_PEN, "20000", "Desde este monto se notifica al Gerente General");
        parametro(ClavesParametro.MARGEN_REP_MIN, "15", "Margen mínimo de repuestos (%)");
        parametro(ClavesParametro.MARGEN_REP_DEFAULT, "35", "Margen por defecto de repuestos (%)");
        parametro(ClavesParametro.MARGEN_REP_MAX, "45", "Margen máximo de repuestos (%)");
    }

    @Test
    void elJefeCambiaUnValorYLaLecturaLoVe() throws Exception {
        mockMvc.perform(put("/api/parametros/" + ClavesParametro.MONTO_ALTO_VALOR_PEN)
                        .with(jefe())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"25000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clave").value(ClavesParametro.MONTO_ALTO_VALOR_PEN))
                .andExpect(jsonPath("$.valor").value("25000"))
                .andExpect(jsonPath("$.descripcion").value("Desde este monto se notifica al Gerente General"))
                .andExpect(jsonPath("$.editadoEn").exists());

        assertEquals("25000", parametrosService.get(ClavesParametro.MONTO_ALTO_VALOR_PEN));
        ParametroSistema guardado = parametros.findById(ClavesParametro.MONTO_ALTO_VALOR_PEN).orElseThrow();
        assertEquals(idJefe, guardado.getEditadoPor());
        assertNotNull(guardado.getEditadoEn());

        mockMvc.perform(get("/api/parametros").with(jefe()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.clave=='MONTO_ALTO_VALOR_PEN')].valor").value("25000"));
    }

    @Test
    void unaClaveQueNoExisteNoSeCrea() throws Exception {
        mockMvc.perform(put("/api/parametros/NO_EXISTE")
                        .with(jefe())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"1\"}"))
                .andExpect(status().isBadRequest());

        assertFalse(parametros.findById("NO_EXISTE").isPresent());
    }

    @Test
    void elValorEnBlancoSeRechaza() throws Exception {
        mockMvc.perform(put("/api/parametros/" + ClavesParametro.MONTO_ALTO_VALOR_PEN)
                        .with(jefe())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"   \"}"))
                .andExpect(status().isBadRequest());

        assertEquals("20000", parametrosService.get(ClavesParametro.MONTO_ALTO_VALOR_PEN));
    }

    @Test
    void elMinimoPorEncimaDelDefectoSeRechaza() throws Exception {
        mockMvc.perform(put("/api/parametros/" + ClavesParametro.MARGEN_REP_MIN)
                        .with(jefe())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"40\"}"))
                .andExpect(status().isBadRequest());

        assertEquals("15", parametrosService.get(ClavesParametro.MARGEN_REP_MIN));
        assertEquals("35", parametrosService.get(ClavesParametro.MARGEN_REP_DEFAULT));
    }

    @Test
    void quienNoEsJefeNoPuedeHacerElPut() throws Exception {
        mockMvc.perform(put("/api/parametros/" + ClavesParametro.MONTO_ALTO_VALOR_PEN)
                        .with(user("vendedor").roles("VENDEDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"1\"}"))
                .andExpect(status().isForbidden());

        assertEquals("20000", parametrosService.get(ClavesParametro.MONTO_ALTO_VALOR_PEN));
    }

    private UserRequestPostProcessor jefe() {
        return user(JEFE).roles("JEFE_ADMINISTRATIVO");
    }

    private void parametro(String clave, String valor, String descripcion) {
        ParametroSistema parametro = new ParametroSistema();
        parametro.setClave(clave);
        parametro.setValor(valor);
        parametro.setDescripcion(descripcion);
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
