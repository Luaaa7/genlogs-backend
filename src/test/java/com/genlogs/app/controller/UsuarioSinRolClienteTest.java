package com.genlogs.app.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.model.Rol;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.UsuarioRepository;

/**
 * Los usuarios CLIENTE solo nacen del registro del portal (con RUC) y quedan unidos a su empresa.
 * Desde «Usuarios» no se puede crear ni pasar a nadie al rol CLIENTE: quedaría sin empresa
 * y vería el portal vacío.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UsuarioSinRolClienteTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private static final String MENSAJE = "Los usuarios del portal se crean solo con el registro por RUC";

    @Test
    void noSeCreaUnUsuarioConRolClienteDesdeUsuarios() throws Exception {
        Rol cliente = rol("CLIENTE");
        String cuerpo = cuerpoCrear(cliente.getIdRol(), "sinempresa", "sinempresa@genlogs.pe");

        mockMvc.perform(post("/api/usuarios").with(user("jefe").roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(MENSAJE)));

        assertFalse(usuarioRepository.existsByNombreUsuario("sinempresa"));
    }

    @Test
    void unUsuarioDelPersonalSeSigueCreandoNormal() throws Exception {
        Rol asistente = rol("ASISTENTE_LOGISTICO");
        String cuerpo = cuerpoCrear(asistente.getIdRol(), "asist.nuevo", "asist.nuevo@genlogs.pe");

        mockMvc.perform(post("/api/usuarios").with(user("jefe").roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());
    }

    @Test
    void noSePasaAUnUsuarioDelPersonalAlRolCliente() throws Exception {
        Rol asistente = rol("ASISTENTE_LOGISTICO");
        Rol cliente = rol("CLIENTE");
        Usuario u = usuario(asistente, "asist.cambio");

        mockMvc.perform(put("/api/usuarios/" + u.getIdUsuario()).with(user("jefe").roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoEditar(cliente.getIdRol(), "asist.cambio@genlogs.pe")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(MENSAJE)));

        assertEquals("ASISTENTE_LOGISTICO", usuarioRepository.findById(u.getIdUsuario()).orElseThrow().getRol().getNombreRol());
    }

    @Test
    void aUnUsuarioClienteSeLePuedeCorregirElNombre() throws Exception {
        Rol cliente = rol("CLIENTE");
        Usuario u = usuario(cliente, "cliente.portal");

        mockMvc.perform(put("/api/usuarios/" + u.getIdUsuario()).with(user("jefe").roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoEditar(cliente.getIdRol(), "cliente.portal@genlogs.pe")))
                .andExpect(status().isOk());
    }

    private String cuerpoCrear(Integer idRol, String nombreUsuario, String correo) {
        return "{\"idRol\":" + idRol + ",\"nombreUsuario\":\"" + nombreUsuario + "\",\"nombres\":\"Prueba\","
                + "\"correo\":\"" + correo + "\",\"password\":\"clave1234\",\"iniciales\":\"PR\"}";
    }

    private String cuerpoEditar(Integer idRol, String correo) {
        return "{\"idRol\":" + idRol + ",\"nombres\":\"Nombre corregido\",\"correo\":\"" + correo + "\",\"iniciales\":\"NC\"}";
    }

    private Rol rol(String nombre) {
        return rolRepository.findByNombreRolIgnoreCase(nombre).orElseGet(() -> {
            Rol r = new Rol();
            r.setNombreRol(nombre);
            r.setDescripcion("Prueba");
            r.setUserCreate("test");
            r.setProcessCreate("TEST");
            r.setDateCreate(LocalDateTime.now());
            r.setStatus("A");
            return rolRepository.save(r);
        });
    }

    private Usuario usuario(Rol rol, String nombreUsuario) {
        Usuario u = new Usuario();
        u.setRol(rol);
        u.setNombreUsuario(nombreUsuario);
        u.setNombres("Prueba");
        u.setCorreo(nombreUsuario + "@genlogs.pe");
        u.setIniciales("PR");
        u.setPasswordHash("x");
        u.setBloqueado(false);
        u.setIntentosFallidos((short) 0);
        u.setUserCreate("test");
        u.setProcessCreate("TEST");
        u.setDateCreate(LocalDateTime.now());
        u.setStatus("A");
        return usuarioRepository.save(u);
    }
}
