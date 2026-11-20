package com.genlogs.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.service.ContactoPublicoService;
import com.genlogs.app.service.MailService;
import com.genlogs.app.service.NotificacionService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicoRucContactoTest {

    private static final String CONTACTO = """
            {"nombre":"Luis","correo":"luis@mina.pe","telefono":"999111222",
             "mensaje":"Necesito fajas para la unidad. Precio aparte."}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContactoPublicoService contactoPublicoService;

    @MockitoBean
    private MailService mail;

    @MockitoBean
    private NotificacionService avisos;

    @AfterEach
    void sinDestino() {
        ReflectionTestUtils.setField(contactoPublicoService, "destino", "");
    }

    @Test
    void rucDeOnceDigitosQueNoEmpiezaPor10Ni20Responde400() throws Exception {
        mockMvc.perform(get("/api/public/ruc/30123456789"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ingresa un RUC válido"));
    }

    @Test
    void laConsultaNumeroOnceDelMismoIpResponde429() throws Exception {
        String ip = "203.0.113.60";
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/public/ruc/20111111111").header("X-Forwarded-For", ip))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/public/ruc/20111111111").header("X-Forwarded-For", ip))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void elContactoPasadoElLimiteResponde429() throws Exception {
        ReflectionTestUtils.setField(contactoPublicoService, "destino", "karla@genlogs.pe");
        when(mail.enviar(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        String ip = "203.0.113.80";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/public/contacto").header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON).content(CONTACTO))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/public/contacto").header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON).content(CONTACTO))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void elContactoResponde200EnviadoTrueYFalseSiBrevoCae() throws Exception {
        ReflectionTestUtils.setField(contactoPublicoService, "destino", "karla@genlogs.pe");
        when(mail.enviar(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/public/contacto").header("X-Forwarded-For", "203.0.113.81")
                        .contentType(MediaType.APPLICATION_JSON).content(CONTACTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enviado").value(true));

        when(mail.enviar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Brevo caido"));

        mockMvc.perform(post("/api/public/contacto").header("X-Forwarded-For", "203.0.113.82")
                        .contentType(MediaType.APPLICATION_JSON).content(CONTACTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enviado").value(false));
    }

    @Test
    void elAvisoDeLaCampanaTraeNombreCorreoTelefonoYMensaje() throws Exception {
        ReflectionTestUtils.setField(contactoPublicoService, "destino", "karla@genlogs.pe");
        when(mail.enviar(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/public/contacto").header("X-Forwarded-For", "203.0.113.83")
                        .contentType(MediaType.APPLICATION_JSON).content(CONTACTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enviado").value(false));

        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(avisos).avisarRol(eq(RolSistema.JEFE_ADMINISTRATIVO), eq(TipoAviso.GENERAL),
                anyString(), mensaje.capture(), any(), eq(false));
        assertThat(mensaje.getValue())
                .contains("Luis")
                .contains("luis@mina.pe")
                .contains("999111222")
                .contains("Necesito fajas para la unidad. Precio aparte.");
    }

    @Test
    void sinContactoEmailRespondeFalseYAvisaEnLaCampana() throws Exception {
        ReflectionTestUtils.setField(contactoPublicoService, "destino", "");

        mockMvc.perform(post("/api/public/contacto").header("X-Forwarded-For", "203.0.113.84")
                        .contentType(MediaType.APPLICATION_JSON).content(CONTACTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enviado").value(false));

        verify(avisos).avisarRol(eq(RolSistema.JEFE_ADMINISTRATIVO), eq(TipoAviso.GENERAL),
                anyString(), anyString(), any(), anyBoolean());
    }
}
