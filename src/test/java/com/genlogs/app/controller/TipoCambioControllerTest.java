package com.genlogs.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.enums.FuenteTipoCambio;
import com.genlogs.app.model.Auditable;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.TipoCambio;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.TipoCambioRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.FactilizaTipoCambioClient;
import com.genlogs.app.service.TipoCambioDiarioService;

/**
 * BE-D · Tipo de cambio (D-19, decisión 8.4): consulta diaria a Factiliza guardada como SUNAT,
 * respaldo al último valor si falla (con aviso al Jefe Adm.) y corrección manual del Jefe Adm.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TipoCambioControllerTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TipoCambioDiarioService diario;
    @Autowired private TipoCambioRepository tipoCambioRepository;
    @Autowired private MonedaRepository monedaRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private FactilizaTipoCambioClient factiliza;

    private Moneda usd;
    private LocalDate hoy;

    @BeforeEach
    void preparar() {
        hoy = LocalDate.now(LIMA);
        usd = monedaRepository.findByCodigoMoneda("USD").orElseGet(() -> monedaRepository.save(auditar(new Moneda(), m -> {
            m.setCodigoMoneda("USD");
            m.setNombreMoneda("Dólar americano");
            m.setSimbolo("$");
        })));
        usuario("jefe.tc", "JEFE_ADMINISTRATIVO");
    }

    // ------------------------------------------------------------ tarea diaria

    @Test
    void consultaDiaria_guardaElValorDeVentaComoSunat() {
        when(factiliza.consultar(hoy)).thenReturn(Optional.of(
                new FactilizaTipoCambioClient.TipoCambioDia(hoy, new BigDecimal("3.745"), new BigDecimal("3.751"))));

        diario.actualizarHoy();

        TipoCambio guardado = deHoy().orElseThrow();
        assertThat(guardado.getValor()).isEqualByComparingTo("3.751");
        assertThat(guardado.getFuente()).isEqualTo(FuenteTipoCambio.SUNAT);
        assertThat(guardado.getEditadoPor()).isNull();
    }

    @Test
    void consultaDiaria_siFactilizaFalla_noGuardaYAvisaAlJefe() {
        guardar(hoy.minusDays(1), "3.700", FuenteTipoCambio.SUNAT);
        when(factiliza.consultar(hoy)).thenReturn(Optional.empty());

        diario.actualizarHoy();

        assertThat(deHoy()).isEmpty();
        Integer avisos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notificacion WHERE tipo = 'TIPO_CAMBIO' AND mensaje LIKE '%3.70%'", Integer.class);
        assertThat(avisos).isEqualTo(1);
    }

    @Test
    void consultaDiaria_noPisaLaCorreccionManualDelDia() {
        guardar(hoy, "3.760", FuenteTipoCambio.MANUAL);

        diario.actualizarHoy();

        verify(factiliza, never()).consultar(any());
        assertThat(deHoy().orElseThrow().getValor()).isEqualByComparingTo("3.760");
    }

    // ------------------------------------------------------------ endpoints

    @Test
    void personalInterno_veElTipoDeCambioDelDia() throws Exception {
        // El Asistente Logístico entra cuando Karla agregue los roles nuevos a ROLES_INTERNOS.
        guardar(hoy, "3.751", FuenteTipoCambio.SUNAT);
        mockMvc.perform(get("/api/tipo-cambio").with(user("gerente").roles("GERENTE_GENERAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(3.751))
                .andExpect(jsonPath("$.codigoMoneda").value("USD"))
                .andExpect(jsonPath("$.fuente").value("SUNAT"))
                .andExpect(jsonPath("$.esDeHoy").value(true));
    }

    @Test
    void sinNingunValorGuardado_responde404() throws Exception {
        mockMvc.perform(get("/api/tipo-cambio").with(user("gerente").roles("GERENTE_GENERAL")))
                .andExpect(status().isNotFound());
    }

    @Test
    void elCliente_noVeElTipoDeCambio() throws Exception {
        mockMvc.perform(get("/api/tipo-cambio").with(user("cli").roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void jefeAdministrativo_corrigeElDelDiaComoManual() throws Exception {
        guardar(hoy, "3.751", FuenteTipoCambio.SUNAT);
        mockMvc.perform(put("/api/tipo-cambio").with(user("jefe.tc").roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":3.765}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(3.765))
                .andExpect(jsonPath("$.fuente").value("MANUAL"));

        TipoCambio corregido = deHoy().orElseThrow();
        assertThat(corregido.getFuente()).isEqualTo(FuenteTipoCambio.MANUAL);
        assertThat(corregido.getEditadoPor())
                .isEqualTo(usuarioRepository.findByNombreUsuario("jefe.tc").orElseThrow().getIdUsuario());
    }

    @Test
    void correccionFueraDe1a10_responde400() throws Exception {
        mockMvc.perform(put("/api/tipo-cambio").with(user("jefe.tc").roles("JEFE_ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":3751}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void otroRol_noCorrigeElTipoDeCambio() throws Exception {
        mockMvc.perform(put("/api/tipo-cambio").with(user("gerente").roles("GERENTE_GENERAL"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":3.765}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void jefeAdministrativo_puedeVolverAConsultarAhora() throws Exception {
        when(factiliza.consultar(hoy)).thenReturn(Optional.of(
                new FactilizaTipoCambioClient.TipoCambioDia(hoy, new BigDecimal("3.745"), new BigDecimal("3.752"))));

        mockMvc.perform(post("/api/tipo-cambio/consultar").with(user("jefe.tc").roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(3.752))
                .andExpect(jsonPath("$.esDeHoy").value(true));
    }

    // ------------------------------------------------------------ datos de prueba

    private Optional<TipoCambio> deHoy() {
        return tipoCambioRepository.findByFechaAndMoneda_CodigoMoneda(hoy, "USD");
    }

    private void guardar(LocalDate fecha, String valor, FuenteTipoCambio fuente) {
        TipoCambio t = new TipoCambio();
        t.setFecha(fecha);
        t.setMoneda(usd);
        t.setValor(new BigDecimal(valor));
        t.setFuente(fuente);
        tipoCambioRepository.save(t);
    }

    private void usuario(String nombre, String rolNombre) {
        if (usuarioRepository.findByNombreUsuario(nombre).isPresent()) {
            return;
        }
        Rol rol = rolRepository.findByNombreRolIgnoreCase(rolNombre)
                .orElseGet(() -> rolRepository.save(auditar(new Rol(), r -> {
                    r.setNombreRol(rolNombre);
                    r.setDescripcion(rolNombre);
                })));
        usuarioRepository.save(auditar(new Usuario(), u -> {
            u.setRol(rol);
            u.setNombreUsuario(nombre);
            u.setNombres("Jefe TC");
            u.setCorreo(nombre + "@genlogs.pe");
            u.setPasswordHash(passwordEncoder.encode("clave1234"));
            u.setIniciales("JT");
            u.setBloqueado(false);
            u.setIntentosFallidos((short) 0);
        }));
    }

    private <T extends Auditable> T auditar(T entidad, Consumer<T> completar) {
        completar.accept(entidad);
        entidad.setUserCreate("test");
        entidad.setProcessCreate("TEST");
        entidad.setDateCreate(LocalDateTime.now());
        if (entidad.getStatus() == null) {
            entidad.setStatus("A");
        }
        return entidad;
    }
}
