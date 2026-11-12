package com.genlogs.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genlogs.app.model.Auditable;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Departamento;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.EstadoReclamo;
import com.genlogs.app.model.Pais;
import com.genlogs.app.model.Provincia;
import com.genlogs.app.model.Reclamo;
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
import com.genlogs.app.repository.ReclamoRepository;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.SectorEconomicoRepository;
import com.genlogs.app.repository.TerceroRepository;
import com.genlogs.app.repository.TipoDocumentoRepository;
import com.genlogs.app.repository.UsuarioRepository;

/** RF-70: reclamo y calificación desde el portal; el cliente solo ve los de su empresa. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PortalReclamoTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(500);

    @Autowired private MockMvc mockMvc;
    @Autowired private ReclamoRepository reclamoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private TerceroRepository terceroRepository;
    @Autowired private SectorEconomicoRepository sectorEconomicoRepository;
    @Autowired private TipoDocumentoRepository tipoDocumentoRepository;
    @Autowired private PaisRepository paisRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private ProvinciaRepository provinciaRepository;
    @Autowired private DistritoRepository distritoRepository;

    @Test
    void elClienteRegistraSuReclamoYSoloVeLosDeSuEmpresa() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        Distrito d = distrito(n);
        String a = usuarioPortal("rec.a." + n, cliente("2081" + String.format("%07d", n), "Empresa A " + n, d));
        String b = usuarioPortal("rec.b." + n, cliente("2082" + String.format("%07d", n), "Empresa B " + n, d));

        String creado = mockMvc.perform(post("/api/portal/reclamos").with(user(a).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asunto\":\"Faja llegó dañada\",\"descripcion\":\"La faja tiene un corte de 10 cm\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoReclamo").value(Matchers.matchesPattern("REC-\\d{6}")))
                .andExpect(jsonPath("$.estado").value("REGISTRADO"))
                .andExpect(jsonPath("$.puedeCalificar").value(false))
                .andExpect(jsonPath("$.historial[0].estado").value("REGISTRADO"))
                // Nada interno en la respuesta del portal.
                .andExpect(jsonPath("$.prioridad").doesNotExist())
                .andExpect(jsonPath("$.responsable").doesNotExist())
                .andExpect(jsonPath("$.historial[0].comentario").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = new ObjectMapper().readTree(creado).get("idReclamo").asLong();

        Reclamo guardado = reclamoRepository.findById(id).orElseThrow();
        assertEquals("Empresa A " + n, guardado.getCliente().getTercero().getRazonSocial());
        assertEquals("RECLAMO", guardado.getTipo().name());

        mockMvc.perform(get("/api/portal/reclamos").with(user(a).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].asunto").value("Faja llegó dañada"));

        // Otra empresa: no lo ve en su lista ni por id.
        mockMvc.perform(get("/api/portal/reclamos").with(user(b).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/portal/reclamos/" + id).with(user(b).roles("CLIENTE")))
                .andExpect(status().isNotFound());
    }

    @Test
    void calificaUnaSolaVezYSoloCuandoEstaResuelto() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        Distrito d = distrito(n);
        String a = usuarioPortal("cal.a." + n, cliente("2083" + String.format("%07d", n), "Califica A " + n, d));
        String b = usuarioPortal("cal.b." + n, cliente("2084" + String.format("%07d", n), "Califica B " + n, d));
        String creado = mockMvc.perform(post("/api/portal/reclamos").with(user(a).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asunto\":\"Demora\",\"descripcion\":\"El despacho llegó tarde\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = new ObjectMapper().readTree(creado).get("idReclamo").asLong();
        String cuatro = "{\"puntaje\":4,\"comentario\":\"Buena atención\"}";

        // Todavía no está resuelto.
        mockMvc.perform(post("/api/portal/reclamos/" + id + "/calificacion").with(user(a).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuatro))
                .andExpect(status().isBadRequest());

        Reclamo reclamo = reclamoRepository.findById(id).orElseThrow();
        reclamo.setEstado(EstadoReclamo.RESUELTO);
        reclamo.setSolucion("Se reprogramó el despacho");
        reclamoRepository.saveAndFlush(reclamo);

        mockMvc.perform(get("/api/portal/reclamos/" + id).with(user(a).roles("CLIENTE")))
                .andExpect(jsonPath("$.puedeCalificar").value(true))
                .andExpect(jsonPath("$.solucion").value("Se reprogramó el despacho"));

        // Fuera de rango y de otra empresa.
        mockMvc.perform(post("/api/portal/reclamos/" + id + "/calificacion").with(user(a).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"puntaje\":6}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/portal/reclamos/" + id + "/calificacion").with(user(b).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuatro))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/portal/reclamos/" + id + "/calificacion").with(user(a).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuatro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calificacion").value(4))
                .andExpect(jsonPath("$.comentarioCalificacion").value("Buena atención"))
                .andExpect(jsonPath("$.puedeCalificar").value(false));

        // Una sola vez.
        mockMvc.perform(post("/api/portal/reclamos/" + id + "/calificacion").with(user(a).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"puntaje\":1}"))
                .andExpect(status().isBadRequest());
        assertEquals((short) 4, reclamoRepository.findById(id).orElseThrow().getCalificacion());
    }

    @Test
    void ordenDeCompraAjenaOInexistenteYRolInterno() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        Distrito d = distrito(n);
        String a = usuarioPortal("oc.a." + n, cliente("2085" + String.format("%07d", n), "Orden A " + n, d));

        mockMvc.perform(post("/api/portal/reclamos").with(user(a).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asunto\":\"OC\",\"descripcion\":\"Sobre una OC\",\"idOrdenCompra\":999999}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/portal/reclamos").with(user(a).roles("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asunto\":\"\",\"descripcion\":\"\"}"))
                .andExpect(status().isBadRequest());

        // El personal interno usa /api/reclamos, no el portal.
        mockMvc.perform(get("/api/portal/reclamos").with(user("jefe." + n).roles("JEFE_ADMINISTRATIVO")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/portal/reclamos"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ datos de prueba

    private Rol rolCliente() {
        return rolRepository.findByNombreRolIgnoreCase("CLIENTE")
                .orElseGet(() -> rolRepository.save(auditar(new Rol(), e -> {
                    e.setNombreRol("CLIENTE");
                    e.setDescripcion("Portal");
                })));
    }

    private String usuarioPortal(String nombreUsuario, Cliente cliente) {
        Rol rol = rolCliente();
        usuarioRepository.save(auditar(new Usuario(), e -> {
            e.setRol(rol);
            e.setCliente(cliente);
            e.setNombreUsuario(nombreUsuario);
            e.setNombres("Contacto " + nombreUsuario);
            e.setCorreo(nombreUsuario + "@portal.pe");
            e.setPasswordHash("x");
            e.setIniciales("CP");
            e.setBloqueado(false);
            e.setIntentosFallidos((short) 0);
        }));
        return nombreUsuario;
    }

    private Cliente cliente(String documento, String razonSocial, Distrito distrito) {
        TipoDocumento tipo = tipoDocumentoRepository.findAll().stream()
                .filter(t -> "RUC".equals(t.getCodigoTipo()))
                .findFirst()
                .orElseGet(() -> tipoDocumentoRepository.save(auditar(new TipoDocumento(), e -> {
                    e.setCodigoTipo("RUC");
                    e.setNombreTipo("RUC");
                    e.setLongitudDocumento((short) 11);
                    e.setSoloNumerico(true);
                })));
        SectorEconomico sector = sectorEconomicoRepository.save(auditar(new SectorEconomico(), e ->
                e.setNombreSector("Sector " + documento)));
        Tercero tercero = terceroRepository.save(auditar(new Tercero(), e -> {
            e.setTipoDocumento(tipo);
            e.setDistrito(distrito);
            e.setNumeroDocumento(documento);
            e.setRazonSocial(razonSocial);
        }));
        return clienteRepository.save(auditar(new Cliente(), e -> {
            e.setTercero(tercero);
            e.setSectorEconomico(sector);
            e.setSituacion("ACTIVO");
        }));
    }

    private Distrito distrito(int n) {
        Pais pais = paisRepository.save(auditar(new Pais(), e -> {
            e.setCodigoIso(String.format("R%02d", n % 100));
            e.setNombrePais("Pais reclamos " + n);
        }));
        Departamento departamento = departamentoRepository.save(auditar(new Departamento(), e -> {
            e.setPais(pais);
            e.setNombreDepartamento("Dep " + n);
        }));
        Provincia provincia = provinciaRepository.save(auditar(new Provincia(), e -> {
            e.setDepartamento(departamento);
            e.setNombreProvincia("Prov " + n);
        }));
        return distritoRepository.save(auditar(new Distrito(), e -> {
            e.setProvincia(provincia);
            e.setUbigeo(String.format("9%05d", n));
            e.setNombreDistrito("Dist " + n);
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
