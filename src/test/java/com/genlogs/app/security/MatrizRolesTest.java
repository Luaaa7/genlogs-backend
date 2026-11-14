package com.genlogs.app.security;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.model.Auditable;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.UsuarioRepository;

/**
 * BE-A (test/be-a-matriz-roles): la matriz de permisos de la v3.2 con los 7 roles
 * ("GENLOGS — Procesos, roles e impacto en la BD (v3.2)", sección Roles y matriz).
 *
 * <p>Cada caso es un endpoint representativo de un módulo y los roles que la matriz
 * deja entrar. Para cada uno de los 7 roles se comprueba que:
 * <ul>
 *   <li>si la matriz lo permite, la seguridad lo deja pasar (no 401 ni 403; el servicio
 *       puede responder 200, 400 o 404 porque los ids no existen);</li>
 *   <li>si no, recibe 403.</li>
 * </ul>
 * Cada rol usa un usuario real en la base: así un 403 solo puede venir de la seguridad
 * y no de un servicio que no encuentra al usuario de la sesión. Búsqueda y vitrina se
 * prueban con sus interruptores encendidos.
 *
 * <p>Diferencias con la matriz que este test NO cubre todavía, porque hoy el backend
 * las deja más abiertas (cada una es de su bloque; al cerrarlas, agregar su caso aquí):
 * <ul>
 *   <li>GET /api/metas (BE-D, dashboard): Supervisor SST y Asistente Administrativo entran; la matriz no les da acceso.</li>
 * </ul>
 */
@SpringBootTest(properties = {"genlogs.busqueda.habilitada=true", "genlogs.vitrina.habilitada=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MatrizRolesTest {

    /** Los 7 roles de la v3.2, en el orden de la matriz. */
    private static final List<String> ROLES = List.of(
            "GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "CONTADOR_GENERAL", "SUPERVISOR_SST",
            "ASISTENTE_LOGISTICO", "ASISTENTE_ADMINISTRATIVO", "CLIENTE");

    private static final Set<String> INTERNOS = Set.of(
            "GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "CONTADOR_GENERAL", "SUPERVISOR_SST",
            "ASISTENTE_LOGISTICO", "ASISTENTE_ADMINISTRATIVO");

    private static final String NO_EXISTE = "999999";

    @Autowired private MockMvc mockMvc;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    /** Un endpoint de un módulo y quiénes entran según la matriz. */
    private record Caso(String modulo, String metodo, String url, String cuerpo, Set<String> permitidos) {
        @Override
        public String toString() {
            return modulo + " · " + metodo + " " + url;
        }
    }

    private static Caso caso(String modulo, String metodo, String url, String cuerpo, String... permitidos) {
        return new Caso(modulo, metodo, url, cuerpo, Set.of(permitidos));
    }

    static Stream<Caso> matriz() {
        String id = NO_EXISTE;
        return Stream.of(
                // ---- Fuentes de búsqueda (v3.3, L5): configura el Jefe; el Asist. Log. solo ve el estado ----
                caso("Fuentes", "GET", "/api/fuentes", null, "JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO"),
                caso("Fuentes", "PUT", "/api/fuentes/EBAY/credenciales", "{\"clave\":\"x\"}", "JEFE_ADMINISTRATIVO"),
                caso("Fuentes", "PATCH", "/api/fuentes/EBAY/activar", "{\"activa\":false}", "JEFE_ADMINISTRATIVO"),
                caso("Fuentes", "PATCH", "/api/fuentes/EBAY/parametros", "{\"pausaSegundos\":5,\"limiteDiario\":50}",
                        "JEFE_ADMINISTRATIVO"),
                caso("Fuentes", "GET", "/api/fuentes/EBAY/selectores", null, "JEFE_ADMINISTRATIVO"),
                // ---- Solicitudes y plazos: crean y gestionan el Asistente Logístico y el Jefe ----
                caso("Solicitudes", "PATCH", "/api/solicitudes/" + id + "/pausar", "{\"motivo\":\"PROVEEDOR\"}",
                        "ASISTENTE_LOGISTICO", "JEFE_ADMINISTRATIVO"),
                caso("Solicitudes", "PATCH", "/api/solicitudes/" + id + "/reasignar", "{\"idUsuario\":1}",
                        "JEFE_ADMINISTRATIVO"),
                // Ver la lista y el detalle: Gerente y Asistente Administrativo solo ven (matriz: V).
                caso("Solicitudes", "GET", "/api/solicitudes/vencimiento", null,
                        "GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO", "ASISTENTE_ADMINISTRATIVO"),
                caso("Solicitudes", "GET", "/api/solicitudes/" + id + "/historial", null,
                        "GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO", "ASISTENTE_ADMINISTRATIVO"),
                caso("Solicitudes", "GET", "/api/solicitudes/" + id + "/items", null,
                        "GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO", "ASISTENTE_ADMINISTRATIVO"),

                // ---- Búsqueda e historial: ven Gerente y Jefe; captura el Asistente Logístico ----
                caso("Búsqueda", "GET", "/api/sourcing/historial", null,
                        "GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO"),
                caso("Búsqueda", "POST", "/api/sourcing/url/leer", "{\"url\":\"https://ejemplo.com\"}",
                        "ASISTENTE_LOGISTICO"),

                // ---- Cotizaciones y márgenes: sin Contador ni SST; aprueba el Jefe ----
                caso("Cotizaciones", "GET", "/api/cotizaciones", null,
                        "GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO", "ASISTENTE_ADMINISTRATIVO"),
                // El Jefe también crea (decisión del equipo, 13/11/2026).
                caso("Cotizaciones", "POST", "/api/cotizaciones", "{}",
                        "ASISTENTE_LOGISTICO", "JEFE_ADMINISTRATIVO"),
                caso("Cotizaciones", "POST", "/api/cotizaciones/" + id + "/aprobar", null,
                        "JEFE_ADMINISTRATIVO"),

                // ---- Pedidos y OC de la minera: todo el personal ve; el Asistente Administrativo registra ----
                caso("Pedidos y OC", "GET", "/api/ordenes-compra", null, INTERNOS.toArray(String[]::new)),
                caso("Pedidos y OC", "PATCH", "/api/ordenes-compra/" + id + "/confirmar-oc",
                        "{\"numeroOc\":\"4500012345\",\"monto\":10}",
                        "ASISTENTE_ADMINISTRATIVO"),

                // ---- Despacho: sin Contador; avanza el Asistente Logístico; SST aprueba pase y SCTR ----
                caso("Despacho", "GET", "/api/despachos", null,
                        "GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "SUPERVISOR_SST", "ASISTENTE_LOGISTICO",
                        "ASISTENTE_ADMINISTRATIVO"),
                caso("Despacho", "PATCH", "/api/despachos/" + id + "/etapa", "{\"etapa\":\"EN_PREPARACION\"}",
                        "ASISTENTE_LOGISTICO"),
                caso("Despacho", "POST", "/api/despachos/" + id + "/sst",
                        "{\"tipo\":\"PASE\",\"fechaVencimiento\":\"2099-01-01\"}",
                        "SUPERVISOR_SST"),

                // ---- Comprobantes y cobranza: registra el Contador; anula el Jefe (BE-A) ----
                caso("Comprobantes", "PATCH", "/api/facturacion/" + id + "/pago",
                        "{\"montoPagado\":1,\"fechaPago\":\"2026-11-01\",\"medioPago\":\"TRANSFERENCIA\"}",
                        "CONTADOR_GENERAL"),
                caso("Comprobantes", "PATCH", "/api/facturacion/" + id + "/estado", "{\"codigoEstado\":\"ANULADA\"}",
                        "JEFE_ADMINISTRATIVO"),
                // S2: ver comprobantes, sin Supervisor SST ni Asistente Logístico.
                caso("Comprobantes", "GET", "/api/facturacion", null,
                        "GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "CONTADOR_GENERAL", "ASISTENTE_ADMINISTRATIVO"),

                // ---- Reclamos (BE-A): sin Contador ni SST; el Asistente Administrativo solo ve ----
                caso("Reclamos", "GET", "/api/reclamos", null,
                        "GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO", "ASISTENTE_ADMINISTRATIVO"),
                caso("Reclamos", "POST", "/api/reclamos", "{\"tipo\":\"INCIDENCIA\",\"asunto\":\"a\",\"descripcion\":\"b\"}",
                        "GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO"),
                caso("Reclamos", "PATCH", "/api/reclamos/" + id + "/estado", "{\"estado\":\"EN_PROCESO\"}",
                        "GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO"),
                caso("Reclamos", "DELETE", "/api/reclamos/" + id, null,
                        "JEFE_ADMINISTRATIVO"),

                // ---- Dashboard y KPI: la meta del mes la fija el Jefe ----
                caso("Dashboard", "PUT", "/api/metas/2026/11", "{\"metaPorcentaje\":90}",
                        "JEFE_ADMINISTRATIVO"),

                // ---- Vitrina: sin Contador ni SST; publican el Asistente Logístico y el Jefe (D-26) ----
                caso("Vitrina", "GET", "/api/vitrina/productos", null,
                        "GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO", "ASISTENTE_ADMINISTRATIVO"),
                caso("Vitrina", "POST", "/api/vitrina/productos/" + id + "/publicar", null,
                        "JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO"),

                // ---- Servicios: crean el Jefe y el Asistente Logístico ----
                caso("Servicios", "POST", "/api/servicios", "{}",
                        "JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO"),
                // S2: ver servicios, sin Contador ni Supervisor SST.
                caso("Servicios", "GET", "/api/servicios", null,
                        "GERENTE_GENERAL", "JEFE_ADMINISTRATIVO", "ASISTENTE_LOGISTICO", "ASISTENTE_ADMINISTRATIVO"),

                // ---- Usuarios, parámetros, feriados y tipo de cambio (BE-A / BE-B / BE-D) ----
                caso("Administración", "GET", "/api/usuarios", null,
                        "GERENTE_GENERAL", "JEFE_ADMINISTRATIVO"),
                caso("Administración", "PATCH", "/api/usuarios/" + id + "/bloqueo?bloqueado=true", null,
                        "JEFE_ADMINISTRATIVO"),
                caso("Administración", "GET", "/api/usuarios/pendientes-portal", null,
                        "JEFE_ADMINISTRATIVO"),
                caso("Administración", "PATCH", "/api/usuarios/" + id + "/activar", null,
                        "JEFE_ADMINISTRATIVO"),
                caso("Administración", "PUT", "/api/parametros/PLAZO_COMUN_DIAS", "{\"valor\":\"1\"}",
                        "JEFE_ADMINISTRATIVO"),
                caso("Administración", "POST", "/api/feriados", "{\"fecha\":\"2099-12-30\",\"descripcion\":\"Prueba\"}",
                        "JEFE_ADMINISTRATIVO"),
                caso("Administración", "PATCH", "/api/feriados/" + id + "/baja", null,
                        "JEFE_ADMINISTRATIVO"),
                caso("Administración", "PUT", "/api/tipo-cambio", "{\"valor\":3.8}",
                        "JEFE_ADMINISTRATIVO"),
                // Paso 0: parámetros, feriados y tipo de cambio los lee todo el personal (los usan
                // cotizaciones y plazos); solo el Jefe los edita (casos de arriba).
                caso("Administración", "GET", "/api/parametros", null, INTERNOS.toArray(String[]::new)),
                caso("Administración", "GET", "/api/feriados", null, INTERNOS.toArray(String[]::new)),
                caso("Administración", "GET", "/api/tipo-cambio", null, INTERNOS.toArray(String[]::new)),

                // ---- Maestros sin dueño (BE-A): solo el Jefe da de baja o crea catálogos ----
                caso("Maestros", "DELETE", "/api/clientes/" + id, null, "JEFE_ADMINISTRATIVO"),
                caso("Maestros", "DELETE", "/api/proveedores/" + id, null, "JEFE_ADMINISTRATIVO"),
                caso("Maestros", "POST", "/api/catalogos/monedas",
                        "{\"codigoMoneda\":\"ZZZ\",\"nombreMoneda\":\"Prueba\",\"simbolo\":\"Z\"}",
                        "JEFE_ADMINISTRATIVO"),

                // ---- Avisos internos (D-21): todo el personal; el cliente recibe correo ----
                caso("Avisos", "GET", "/api/notificaciones", null, INTERNOS.toArray(String[]::new)));
    }

    @BeforeEach
    void unUsuarioRealPorRol() {
        for (String nombreRol : ROLES) {
            Rol rol = rolRepository.findByNombreRolIgnoreCase(nombreRol)
                    .orElseGet(() -> rolRepository.save(auditar(new Rol(), r -> {
                        r.setNombreRol(nombreRol);
                        r.setDescripcion("Matriz v3.2");
                    })));
            String nombreUsuario = usuarioDe(nombreRol);
            if (usuarioRepository.existsByNombreUsuario(nombreUsuario)) {
                continue;
            }
            usuarioRepository.save(auditar(new Usuario(), u -> {
                u.setRol(rol);
                u.setNombreUsuario(nombreUsuario);
                u.setNombres("Matriz " + nombreRol);
                u.setCorreo(nombreUsuario + "@genlogs.test");
                u.setPasswordHash("x");
                u.setIniciales("MR");
                u.setBloqueado(false);
                u.setIntentosFallidos((short) 0);
            }));
        }
    }

    @Test
    void laMatrizUsaLosSieteRolesDeLaV32() {
        List<String> delSistema = Arrays.stream(RolSistema.values()).map(Enum::name).toList();
        assertEquals(7, ROLES.size(), "La matriz de la v3.2 tiene 7 roles");
        assertEquals(Set.copyOf(delSistema), Set.copyOf(ROLES), "Los roles del test deben ser los de RolSistema");
        assertEquals(Set.of(RolSistema.nombresInternos()), INTERNOS, "Los 6 internos son todos menos CLIENTE");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matriz")
    void cadaRolEntraSoloDondeLaMatrizLoPermite(Caso caso) {
        List<Executable> comprobaciones = new ArrayList<>();
        for (String rol : ROLES) {
            comprobaciones.add(() -> {
                int estado = mockMvc.perform(pedido(caso, rol)).andReturn().getResponse().getStatus();
                if (caso.permitidos().contains(rol)) {
                    assertNotEquals(403, estado, rol + " debería poder: " + caso);
                    assertNotEquals(401, estado, rol + " debería poder: " + caso);
                } else {
                    assertEquals(403, estado, rol + " NO debería poder: " + caso);
                }
            });
        }
        assertAll(caso.toString(), comprobaciones);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rutasDelPortal")
    void elPortalEsSoloDelCliente(String ruta) {
        List<Executable> comprobaciones = new ArrayList<>();
        for (String rol : INTERNOS) {
            comprobaciones.add(() -> assertEquals(403,
                    mockMvc.perform(request(HttpMethod.GET, ruta).with(user(usuarioDe(rol)).roles(rol)))
                            .andReturn().getResponse().getStatus(),
                    rol + " no entra al portal: " + ruta));
        }
        assertAll(ruta, comprobaciones);
    }

    static Stream<Arguments> rutasDelPortal() {
        return Stream.of("/api/portal/pedidos", "/api/portal/reclamos", "/api/portal/cotizaciones",
                "/api/portal/despachos").map(Arguments::of);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matriz")
    void sinSesionTodoResponde401(Caso caso) throws Exception {
        MockHttpServletRequestBuilder sinSesion = request(HttpMethod.valueOf(caso.metodo()), caso.url());
        if (caso.cuerpo() != null) {
            sinSesion = sinSesion.contentType(MediaType.APPLICATION_JSON).content(caso.cuerpo());
        }
        assertEquals(401, mockMvc.perform(sinSesion).andReturn().getResponse().getStatus(), caso.toString());
    }

    private MockHttpServletRequestBuilder pedido(Caso caso, String rol) {
        MockHttpServletRequestBuilder pedido = request(HttpMethod.valueOf(caso.metodo()), caso.url())
                .with(user(usuarioDe(rol)).roles(rol));
        if (caso.cuerpo() != null) {
            pedido = pedido.contentType(MediaType.APPLICATION_JSON).content(caso.cuerpo());
        }
        return pedido;
    }

    private static String usuarioDe(String rol) {
        return "matriz." + rol.toLowerCase();
    }

    private static <T extends Auditable> T auditar(T entidad, Consumer<T> completar) {
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