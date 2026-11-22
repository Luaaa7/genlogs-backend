package com.genlogs.app.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.model.Auditable;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Departamento;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.Pais;
import com.genlogs.app.model.Provincia;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.SectorEconomico;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.TipoDocumento;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.DepartamentoRepository;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.PaisRepository;
import com.genlogs.app.repository.ProvinciaRepository;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.SectorEconomicoRepository;
import com.genlogs.app.repository.TerceroRepository;
import com.genlogs.app.repository.TipoDocumentoRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.security.google.CuentaGoogle;
import com.genlogs.app.security.google.VerificadorTokenGoogle;
import com.genlogs.app.service.NotificacionService;

/** BPMN 8.4 · El Jefe Administrativo revisa los accesos pendientes del portal: los activa o los rechaza. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccesosPendientesPortalTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(900);
    private static final String PENDIENTE = "REGISTRO_PORTAL_PENDIENTE";

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private TerceroRepository terceroRepository;
    @Autowired private SectorEconomicoRepository sectorEconomicoRepository;
    @Autowired private PaisRepository paisRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private ProvinciaRepository provinciaRepository;
    @Autowired private DistritoRepository distritoRepository;
    @Autowired private TipoDocumentoRepository tipoDocumentoRepository;

    @MockitoBean private NotificacionService notificacionService;
    @MockitoBean private VerificadorTokenGoogle verificador;

    @Test
    void elListadoTraeLaEmpresaElRucYDeDondeVieneElPedido() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        Cliente cliente = cliente(n, "Minera Lista " + n);
        Usuario registro = pendienteDeRegistro(n, cliente);
        Usuario google = pendienteDeGoogle(n, cliente);

        mockMvc.perform(get("/api/usuarios/pendientes-portal").with(user("jefe." + n).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idUsuario == " + registro.getIdUsuario() + ")].empresa").value("Minera Lista " + n))
                .andExpect(jsonPath("$[?(@.idUsuario == " + registro.getIdUsuario() + ")].ruc").value(cliente.getTercero().getNumeroDocumento()))
                .andExpect(jsonPath("$[?(@.idUsuario == " + registro.getIdUsuario() + ")].origen").value("REGISTRO"))
                .andExpect(jsonPath("$[?(@.idUsuario == " + registro.getIdUsuario() + ")].fechaSolicitud").isNotEmpty())
                .andExpect(jsonPath("$[?(@.idUsuario == " + google.getIdUsuario() + ")].origen").value("GOOGLE"));
    }

    @Test
    void rechazarUnAccesoDelRegistroLoSacaDePendientesYAvisaAlCliente() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        Usuario pendiente = pendienteDeRegistro(n, cliente(n, "Empresa " + n));

        mockMvc.perform(patch("/api/usuarios/" + pendiente.getIdUsuario() + "/rechazar").with(user("jefe." + n).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isNoContent());

        Usuario rechazado = usuarioRepository.findById(pendiente.getIdUsuario()).orElseThrow();
        assertEquals("I", rechazado.getStatus().trim());
        assertEquals("RECHAZAR_ACCESO_PORTAL", rechazado.getProcessUpdate());
        verify(notificacionService).enviarCorreoCliente(eq(pendiente.getCorreo()), anyString(), contains("no fue aprobado"), anyString());
        mockMvc.perform(get("/api/usuarios/pendientes-portal").with(user("jefe." + n).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(jsonPath("$[?(@.idUsuario == " + pendiente.getIdUsuario() + ")]").isEmpty());
        // Ya no se puede activar ni volver a rechazar.
        mockMvc.perform(patch("/api/usuarios/" + pendiente.getIdUsuario() + "/activar").with(user("jefe." + n).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/usuarios/" + pendiente.getIdUsuario() + "/rechazar").with(user("jefe." + n).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rechazarUnaCuentaDeGoogleYLuegoNoEntraComoPendiente() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        Usuario pendiente = pendienteDeGoogle(n, cliente(n, "Empresa " + n));

        mockMvc.perform(patch("/api/usuarios/" + pendiente.getIdUsuario() + "/rechazar").with(user("jefe." + n).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isNoContent());

        when(verificador.verificar("tok-" + n)).thenReturn(new CuentaGoogle(pendiente.getGoogleSub(), pendiente.getCorreo(), "G"));
        mockMvc.perform(post("/api/portal/google").contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"tok-" + n + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("inactivo")));
    }

    @Test
    void soloElJefeRechazaYSoloAccesosPendientes() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        Cliente cliente = cliente(n, "Empresa " + n);
        Usuario pendiente = pendienteDeRegistro(n, cliente);
        Usuario activo = usuario("activo." + n, "activo" + n + "@empresa.pe", cliente, "A", "TEST");

        mockMvc.perform(patch("/api/usuarios/" + pendiente.getIdUsuario() + "/rechazar").with(user("cli." + n).roles("CLIENTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/usuarios/" + pendiente.getIdUsuario() + "/rechazar").with(user("asis." + n).roles("ASISTENTE_LOGISTICO")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/usuarios/" + activo.getIdUsuario() + "/rechazar").with(user("jefe." + n).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isNotFound());
        assertEquals("A", usuarioRepository.findById(activo.getIdUsuario()).orElseThrow().getStatus().trim());
    }

    // ---------- datos de prueba ----------

    private Usuario pendienteDeRegistro(int n, Cliente cliente) {
        // process_create no se puede cambiar después de insertar (updatable = false): va al crear.
        return usuario("reg." + n, "reg" + n + "@empresa.pe", cliente, "I", PENDIENTE);
    }

    private Usuario pendienteDeGoogle(int n, Cliente cliente) {
        Usuario u = usuario("goo." + n, "goo" + n + "@gmail.com", cliente, "I", "REGISTRO_GOOGLE");
        u.setProcessUpdate(PENDIENTE);
        u.setGoogleSub("sub-" + n);
        u.setDateUpdate(LocalDateTime.now());
        return usuarioRepository.save(u);
    }

    private Usuario usuario(String nombreUsuario, String correo, Cliente cliente, String status, String procesoCreacion) {
        Rol rol = rolRepository.findByNombreRolIgnoreCase("CLIENTE")
                .orElseGet(() -> rolRepository.save(auditar(new Rol(), e -> {
                    e.setNombreRol("CLIENTE");
                    e.setDescripcion("Portal");
                })));
        Usuario nuevo = auditar(new Usuario(), e -> {
            e.setRol(rol);
            e.setCliente(cliente);
            e.setNombreUsuario(nombreUsuario);
            e.setNombres("Contacto " + nombreUsuario);
            e.setCorreo(correo);
            e.setPasswordHash(passwordEncoder.encode("Clave1234"));
            e.setIniciales("CP");
            e.setBloqueado(false);
            e.setIntentosFallidos((short) 0);
            e.setStatus(status);
        });
        nuevo.setProcessCreate(procesoCreacion);
        return usuarioRepository.save(nuevo);
    }

    private Cliente cliente(int n, String razonSocial) {
        TipoDocumento tipo = tipoDocumentoRepository.findAll().stream()
                .filter(t -> "RUC".equals(t.getCodigoTipo()))
                .findFirst()
                .orElseGet(() -> tipoDocumentoRepository.save(auditar(new TipoDocumento(), e -> {
                    e.setCodigoTipo("RUC");
                    e.setNombreTipo("RUC");
                    e.setLongitudDocumento((short) 11);
                    e.setSoloNumerico(true);
                })));
        Pais pais = paisRepository.save(auditar(new Pais(), e -> {
            e.setCodigoIso(String.format("A%02d", n % 100));
            e.setNombrePais("Pais A" + n);
        }));
        Departamento dep = departamentoRepository.save(auditar(new Departamento(), e -> {
            e.setPais(pais);
            e.setNombreDepartamento("Lima");
        }));
        Provincia prov = provinciaRepository.save(auditar(new Provincia(), e -> {
            e.setDepartamento(dep);
            e.setNombreProvincia("Lima");
        }));
        Distrito distrito = distritoRepository.save(auditar(new Distrito(), e -> {
            e.setProvincia(prov);
            e.setUbigeo(String.format("8%05d", n));
            e.setNombreDistrito("Miraflores");
        }));
        SectorEconomico sector = sectorEconomicoRepository.save(auditar(new SectorEconomico(), e -> e.setNombreSector("Sector A" + n)));
        Tercero tercero = terceroRepository.save(auditar(new Tercero(), e -> {
            e.setTipoDocumento(tipo);
            e.setDistrito(distrito);
            e.setNumeroDocumento("2081" + String.format("%07d", n));
            e.setRazonSocial(razonSocial);
        }));
        return clienteRepository.save(auditar(new Cliente(), e -> {
            e.setTercero(tercero);
            e.setSectorEconomico(sector);
            e.setSituacion("ACTIVO");
        }));
    }

    private <T extends Auditable> T auditar(T entidad, java.util.function.Consumer<T> completar) {
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
