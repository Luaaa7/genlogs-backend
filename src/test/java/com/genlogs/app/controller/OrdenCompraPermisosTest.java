package com.genlogs.app.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.genlogs.app.dto.ConfirmarOcRequest;
import com.genlogs.app.service.PermisosOrdenCompra;

import jakarta.validation.Validator;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrdenCompraPermisosTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Validator validator;

    @ParameterizedTest
    @ValueSource(strings = {"GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "CONTADOR_GENERAL"})
    void quienSoloVeListaYNoEscribe(String rol) throws Exception {
        mockMvc.perform(get("/api/ordenes-compra").with(user("u").roles(rol)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ordenes-compra")
                        .with(user("u").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCotizacion\":1,\"numeroOrdenCompra\":\"0001-10\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/ordenes-compra/1/estado")
                        .with(user("u").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoEstado\":\"RECIBIDA\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/ordenes-compra/1/archivo")
                        .file(new MockMultipartFile("archivo", "oc.pdf", "application/pdf", new byte[] {'%'}))
                        .with(user("u").roles(rol)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/ordenes-compra/1/confirmar-oc")
                        .with(user("u").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroOc\":\"4500012345\",\"monto\":10.50}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "VENDEDOR"})
    void administradorYVendedorSiguenCreandoYNoConfirman(String rol) throws Exception {
        mockMvc.perform(get("/api/ordenes-compra").with(user("u").roles(rol)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ordenes-compra")
                        .with(user("u").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCotizacion\":1,\"numeroOrdenCompra\":\"0001-10\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/ordenes-compra/1/confirmar-oc")
                        .with(user("u").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroOc\":\"4500012345\",\"monto\":10.50}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void elClienteSigueFuera() throws Exception {
        mockMvc.perform(get("/api/ordenes-compra").with(user("u").roles("CLIENTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/ordenes-compra/1/archivo")
                        .file(new MockMultipartFile("archivo", "oc.pdf", "application/pdf", new byte[] {'%'}))
                        .with(user("u").roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    /**
     * Desde feat/be-a-security-config estos roles pasan el filtro de URL (ROLES_INTERNOS v3.2).
     * Lo que pueden hacer lo decide PermisosOrdenCompra: ven (matriz: Pedidos y OC, V) pero no gestionan.
     */
    @ParameterizedTest
    @ValueSource(strings = {"ASISTENTE_LOGISTICO", "SUPERVISOR_SST"})
    void asistenteLogisticoYSupervisorSstSoloVen(String rol) throws Exception {
        mockMvc.perform(get("/api/ordenes-compra").with(user("u").roles(rol)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ordenes-compra")
                        .with(user("u").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCotizacion\":1,\"numeroOrdenCompra\":\"0001-10\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/ordenes-compra/1/archivo")
                        .file(new MockMultipartFile("archivo", "oc.pdf", "application/pdf", new byte[] {'%'}))
                        .with(user("u").roles(rol)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/ordenes-compra/1/confirmar-oc")
                        .with(user("u").roles(rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroOc\":\"4500012345\",\"monto\":10.50}"))
                .andExpect(status().isForbidden());
    }

    /** El Asistente Administrativo ve y gestiona la OC (RF-85): pasa el permiso y la cotización 1 no existe. */
    @Test
    void elAsistenteAdministrativoVeYGestiona() throws Exception {
        mockMvc.perform(get("/api/ordenes-compra").with(user("u").roles("ASISTENTE_ADMINISTRATIVO")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ordenes-compra")
                        .with(user("u").roles("ASISTENTE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCotizacion\":1,\"numeroOrdenCompra\":\"0001-10\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmarRechazaNumeroVacioMontoNegativoYNoNumerico() throws Exception {
        ConfirmarOcRequest vacio = new ConfirmarOcRequest();
        vacio.setNumeroOc(" ");
        vacio.setMonto(new BigDecimal("-1"));
        assertFalse(validator.validate(vacio).isEmpty());

        ConfirmarOcRequest decimales = new ConfirmarOcRequest();
        decimales.setNumeroOc("4500012345");
        decimales.setMonto(new BigDecimal("10.555"));
        assertFalse(validator.validate(decimales).isEmpty());

        assertThrows(InvalidFormatException.class, () -> new ObjectMapper().readValue(
                "{\"numeroOc\":\"4500012345\",\"monto\":\"no-es-numero\"}", ConfirmarOcRequest.class));

        ConfirmarOcRequest monedaCorta = new ConfirmarOcRequest();
        monedaCorta.setNumeroOc("4500012345");
        monedaCorta.setMonto(new BigDecimal("10.50"));
        monedaCorta.setMoneda("US");
        assertTrue(validator.validate(monedaCorta).stream()
                .anyMatch(error -> error.getMessage().contains("código de 3 letras")));

        ConfirmarOcRequest monedaValida = new ConfirmarOcRequest();
        monedaValida.setNumeroOc("4500012345");
        monedaValida.setMonto(new BigDecimal("10.50"));
        monedaValida.setMoneda("usd");
        assertTrue(validator.validate(monedaValida).isEmpty());

        assertFalse(PermisosOrdenCompra.VER.contains("CLIENTE"));
        assertFalse(PermisosOrdenCompra.GESTIONAR.contains("JEFE_ADMINISTRATIVO"));
    }

    @Test
    void monedaInvalidaAlConfirmarResponde400() throws Exception {
        org.springframework.validation.beanvalidation.LocalValidatorFactoryBean validador =
                new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();
        validador.afterPropertiesSet();
        org.springframework.test.web.servlet.MockMvc suelto = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new OrdenCompraController(
                        org.mockito.Mockito.mock(com.genlogs.app.service.OrdenCompraService.class),
                        org.mockito.Mockito.mock(com.genlogs.app.service.FacturacionService.class)))
                .setControllerAdvice(new com.genlogs.app.exception.GlobalExceptionHandler())
                .setValidator(validador)
                .build();

        suelto.perform(patch("/api/ordenes-compra/1/confirmar-oc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroOc\":\"4500012345\",\"monto\":10.50,\"moneda\":\"US\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("código de 3 letras")));
    }
}
