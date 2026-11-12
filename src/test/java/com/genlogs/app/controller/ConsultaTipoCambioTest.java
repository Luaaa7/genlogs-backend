package com.genlogs.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.TipoCambioDia;
import com.genlogs.app.enums.FuenteTipoCambio;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.model.Auditable;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.TipoCambio;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.NotificacionRepository;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.TipoCambioRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.ConsultaTipoCambio;
import com.genlogs.app.service.ConsultaTipoCambioService;
import com.genlogs.app.service.TipoCambioService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConsultaTipoCambioTest {

    private static final String JEFE = "jefe.tipo.cambio";

    @Autowired private MockMvc mockMvc;
    @Autowired private ConsultaTipoCambioService consulta;
    @Autowired private TipoCambioService tipoCambioService;
    @Autowired private TipoCambioRepository tipoCambios;
    @Autowired private MonedaRepository monedas;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private NotificacionRepository notificaciones;
    @MockitoBean private ConsultaTipoCambio cliente;

    private Moneda dolar;
    private Long idJefe;
    private LocalDate hoy;

    @BeforeEach
    void datos() {
        hoy = LocalDate.now(ZoneId.of("America/Lima"));
        dolar = monedas.save(auditar(new Moneda(), m -> {
            m.setCodigoMoneda("USD");
            m.setNombreMoneda("Dolar prueba tipo cambio");
            m.setSimbolo("$");
        }));
        Rol rol = roles.save(auditar(new Rol(), r -> {
            r.setNombreRol("JEFE_ADMINISTRATIVO");
            r.setDescripcion("Jefe");
        }));
        idJefe = usuarios.save(auditar(new Usuario(), u -> {
            u.setRol(rol);
            u.setNombreUsuario(JEFE);
            u.setNombres("Jefe Tipo Cambio");
            u.setCorreo("jefe.tipo.cambio@genlogs.test");
            u.setPasswordHash("{noop}demo");
            u.setIniciales("JT");
            u.setBloqueado(false);
            u.setIntentosFallidos((short) 0);
        })).getIdUsuario();
    }

    @Test
    void unaConsultaExitosaDejaElUsdDeHoyEnSunat() throws Exception {
        when(cliente.ventaDelDia(hoy)).thenReturn(new BigDecimal("3.781"));

        consulta.consultarHoy();

        TipoCambioDia dia = tipoCambioService.delDia().orElseThrow();
        assertTrue(dia.esDeHoy());
        assertEquals(hoy, dia.fecha());
        assertEquals("USD", dia.codigoMoneda());
        assertEquals(0, dia.valor().compareTo(new BigDecimal("3.7810")));
        assertEquals(FuenteTipoCambio.SUNAT, dia.fuente());
        TipoCambio fila = tipoCambios.findByMoneda_CodigoMonedaAndFecha("USD", hoy).orElseThrow();
        assertNull(fila.getEditadoPor());
        assertEquals(0, avisos());

        mockMvc.perform(get("/api/tipo-cambio").with(jefe()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(hoy.toString()))
                .andExpect(jsonPath("$.codigoMoneda").value("USD"))
                .andExpect(jsonPath("$.fuente").value("SUNAT"))
                .andExpect(jsonPath("$.esDeHoy").value(true));
    }

    @Test
    void siFactilizaFallaNoHayFilaNuevaYSeAvisa() {
        fila(hoy.minusDays(1), "3.7000", FuenteTipoCambio.SUNAT, null);
        when(cliente.ventaDelDia(any())).thenThrow(new IllegalStateException("sin llave"));

        consulta.consultarHoy();

        assertFalse(tipoCambios.findByMoneda_CodigoMonedaAndFecha("USD", hoy).isPresent());
        TipoCambioDia dia = tipoCambioService.delDia().orElseThrow();
        assertFalse(dia.esDeHoy());
        assertEquals(hoy.minusDays(1), dia.fecha());
        assertEquals(1, avisos());
    }

    @Test
    void siHoyYaEstaEnManualLaTareaNoLoCambia() {
        fila(hoy, "3.5000", FuenteTipoCambio.MANUAL, idJefe);

        consulta.consultarHoy();

        TipoCambio fila = tipoCambios.findByMoneda_CodigoMonedaAndFecha("USD", hoy).orElseThrow();
        assertEquals(FuenteTipoCambio.MANUAL, fila.getFuente());
        assertEquals(0, fila.getValor().compareTo(new BigDecimal("3.5000")));
        assertEquals(idJefe, fila.getEditadoPor());
        verify(cliente, never()).ventaDelDia(any());
        assertEquals(0, avisos());
    }

    @Test
    void elJefeGuardaUnValorValido() throws Exception {
        mockMvc.perform(put("/api/tipo-cambio")
                        .with(jefe())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"3.7520\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuente").value("MANUAL"))
                .andExpect(jsonPath("$.esDeHoy").value(true))
                .andExpect(jsonPath("$.valor").value(3.7520));

        TipoCambioDia dia = tipoCambioService.delDia().orElseThrow();
        assertEquals(FuenteTipoCambio.MANUAL, dia.fuente());
        assertTrue(dia.esDeHoy());
        assertEquals(idJefe, tipoCambios.findByMoneda_CodigoMonedaAndFecha("USD", hoy).orElseThrow().getEditadoPor());
    }

    @Test
    void unValorVacioONoPositivoSeRechaza() throws Exception {
        fila(hoy.minusDays(1), "3.7000", FuenteTipoCambio.SUNAT, null);

        mockMvc.perform(put("/api/tipo-cambio")
                        .with(jefe())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/tipo-cambio")
                        .with(jefe())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/tipo-cambio")
                        .with(jefe())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":-1}"))
                .andExpect(status().isBadRequest());

        assertFalse(tipoCambios.findByMoneda_CodigoMonedaAndFecha("USD", hoy).isPresent());
        assertEquals(hoy.minusDays(1), tipoCambioService.delDia().orElseThrow().fecha());
    }

    @Test
    void quienNoEsJefeNoPuedeHacerElPut() throws Exception {
        mockMvc.perform(put("/api/tipo-cambio")
                        .with(user("vendedor").roles("VENDEDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"3.8000\"}"))
                .andExpect(status().isForbidden());

        assertFalse(tipoCambios.findByMoneda_CodigoMonedaAndFecha("USD", hoy).isPresent());
    }

    private long avisos() {
        return notificaciones.findAll().stream()
                .filter(n -> TipoAviso.TIPO_CAMBIO.name().equals(n.getTipo()))
                .count();
    }

    private void fila(LocalDate fecha, String valor, FuenteTipoCambio fuente, Long editadoPor) {
        TipoCambio fila = new TipoCambio();
        fila.setFecha(fecha);
        fila.setMoneda(dolar);
        fila.setValor(new BigDecimal(valor));
        fila.setFuente(fuente);
        fila.setEditadoPor(editadoPor);
        tipoCambios.saveAndFlush(fila);
    }

    private UserRequestPostProcessor jefe() {
        return user(JEFE).roles("JEFE_ADMINISTRATIVO");
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
