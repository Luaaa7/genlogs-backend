package com.genlogs.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.NotificacionRepository;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.ContactoPublicoService;
import com.genlogs.app.service.MailService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContactoMensajeLargoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContactoPublicoService contactoPublicoService;

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private MailService mail;

    @AfterEach
    void sinDestino() {
        ReflectionTestUtils.setField(contactoPublicoService, "destino", "");
    }

    @Test
    void mensajeDe2000ConBrevoCaidoGuardaElAvisoCompleto() throws Exception {
        jefe();
        ReflectionTestUtils.setField(contactoPublicoService, "destino", "karla@genlogs.pe");
        when(mail.enviar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Brevo caido"));

        String mensaje = "m".repeat(2000);
        String cuerpo = new ObjectMapper().writeValueAsString(Map.of(
                "nombre", "Luis",
                "correo", "luis@mina.pe",
                "telefono", "999111222",
                "mensaje", mensaje));

        mockMvc.perform(post("/api/public/contacto").header("X-Forwarded-For", "203.0.113.90")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enviado").value(false));

        String guardado = notificacionRepository.findAll().stream()
                .map(aviso -> aviso.getMensaje())
                .filter(texto -> texto != null && texto.contains(mensaje))
                .findFirst()
                .orElse("");
        assertThat(guardado).contains("Luis", "luis@mina.pe", "999111222", mensaje);
        assertThat(guardado).hasSizeGreaterThanOrEqualTo(mensaje.length());
    }

    private void jefe() {
        Rol rol = rolRepository.findByNombreRolIgnoreCase("JEFE_ADMINISTRATIVO").orElseGet(() -> {
            Rol nuevo = new Rol();
            nuevo.setNombreRol("JEFE_ADMINISTRATIVO");
            nuevo.setDescripcion("Jefe");
            nuevo.setStatus("A");
            nuevo.setUserCreate("test");
            nuevo.setProcessCreate("test");
            nuevo.setDateCreate(LocalDateTime.now());
            return rolRepository.save(nuevo);
        });
        if (usuarioRepository.findAll().stream().anyMatch(u -> "A".equals(u.getStatus())
                && u.getRol() != null
                && "JEFE_ADMINISTRATIVO".equalsIgnoreCase(u.getRol().getNombreRol())
                && Boolean.FALSE.equals(u.getBloqueado()))) {
            return;
        }
        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        usuario.setNombreUsuario("jefe.aviso");
        usuario.setNombres("Jefe Aviso");
        usuario.setCorreo("jefe.aviso@genlogs.pe");
        usuario.setPasswordHash("$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ012");
        usuario.setIniciales("JA");
        usuario.setBloqueado(false);
        usuario.setIntentosFallidos((short) 0);
        usuario.setStatus("A");
        usuario.setUserCreate("test");
        usuario.setProcessCreate("test");
        usuario.setDateCreate(LocalDateTime.now());
        usuarioRepository.save(usuario);
    }
}
