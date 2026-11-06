package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.CambiarEstadoReclamoRequest;
import com.genlogs.app.dto.ComentarioReclamoRequest;
import com.genlogs.app.dto.PaginaResponse;
import com.genlogs.app.dto.ReclamoRequest;
import com.genlogs.app.dto.ReclamoResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.EstadoReclamo;
import com.genlogs.app.model.PrioridadReclamo;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.TipoReclamo;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.UsuarioRepository;

/** Integración del módulo de reclamos/incidencias contra H2 (sin mocks). */
@SpringBootTest
@ActiveProfiles("test")
@Sql("/schema.sql")
@Transactional
class ReclamoIntegracionTest {

    @Autowired
    private ReclamoService reclamoService;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario usuario;

    @BeforeEach
    void preparar() {
        Rol rol = new Rol();
        rol.setNombreRol("JEFE_ADMINISTRATIVO");
        rol.setDescripcion("Jefe administrativo");
        auditar(rol);
        rol = rolRepository.save(rol);

        Usuario u = new Usuario();
        u.setRol(rol);
        u.setNombreUsuario("jefe");
        u.setNombres("Jefe Demo");
        u.setCorreo("jefe@genlogs.pe");
        u.setPasswordHash("{noop}demo");
        u.setIniciales("JD");
        u.setBloqueado(false);
        u.setIntentosFallidos((short) 0);
        auditar(u);
        usuario = usuarioRepository.save(u);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "jefe", null, List.of(new SimpleGrantedAuthority("ROLE_JEFE_ADMINISTRATIVO"))));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void flujoCompletoDeUnaIncidencia() {
        ReclamoResponse creado = reclamoService.crear(incidencia("Demora en despacho"));

        assertEquals("INC-000001", creado.getCodigoReclamo());
        assertEquals("REGISTRADO", creado.getEstado());
        assertEquals("ALTA", creado.getPrioridad());
        assertEquals(usuario.getIdUsuario(), creado.getIdUsuarioResponsable());
        assertEquals(1, creado.getSeguimientos().size());
        assertTrue(creado.getVencido(), "fecha límite ya pasada y sigue abierto");

        Long id = creado.getIdReclamo();
        reclamoService.cambiarEstado(id, estado(EstadoReclamo.EN_PROCESO, "Revisando con almacén"));
        reclamoService.agregarComentario(id, comentario("Se llamó al transportista"));

        // RESUELTO exige la solución
        assertThrows(BusinessException.class,
                () -> reclamoService.cambiarEstado(id, estado(EstadoReclamo.RESUELTO, " ")));
        ReclamoResponse resuelto = reclamoService.cambiarEstado(id, estado(EstadoReclamo.RESUELTO, "Se reprogramó"));
        assertEquals("Se reprogramó", resuelto.getSolucion());
        assertFalse(resuelto.getVencido());

        ReclamoResponse cerrado = reclamoService.cambiarEstado(id, estado(EstadoReclamo.CERRADO, null));
        assertNotNull(cerrado.getFechaCierre());
        assertTrue(cerrado.getSiguientesEstados().isEmpty());
        assertEquals(5, cerrado.getSeguimientos().size());

        // Final: no se edita ni cambia de estado
        assertThrows(BusinessException.class,
                () -> reclamoService.cambiarEstado(id, estado(EstadoReclamo.EN_PROCESO, null)));
        assertThrows(BusinessException.class,
                () -> reclamoService.actualizar(id, incidencia("Otro asunto")));
    }

    @Test
    void transicionNoPermitidaYReclamoSinCliente() {
        ReclamoResponse creado = reclamoService.crear(incidencia("Falla de equipo"));
        assertThrows(BusinessException.class, () -> reclamoService.cambiarEstado(
                creado.getIdReclamo(), estado(EstadoReclamo.CERRADO, null)));

        ReclamoRequest reclamo = incidencia("Producto dañado");
        reclamo.setTipo(TipoReclamo.RECLAMO);
        assertThrows(BusinessException.class, () -> reclamoService.crear(reclamo));
    }

    @Test
    void listadoConFiltros() {
        reclamoService.crear(incidencia("Uno"));
        ReclamoResponse dos = reclamoService.crear(incidencia("Dos"));
        reclamoService.cambiarEstado(dos.getIdReclamo(), estado(EstadoReclamo.EN_PROCESO, null));

        PaginaResponse<ReclamoResponse> todos = reclamoService.listar(null, null, null, null, null, 0, 20);
        assertEquals(2, todos.getTotalElements());

        PaginaResponse<ReclamoResponse> enProceso = reclamoService.listar(
                TipoReclamo.INCIDENCIA, EstadoReclamo.EN_PROCESO, null, null, usuario.getIdUsuario(), 0, 20);
        assertEquals(1, enProceso.getTotalElements());
        assertEquals("INC-000002", enProceso.getContent().get(0).getCodigoReclamo());

        reclamoService.eliminar(dos.getIdReclamo());
        assertEquals(1, reclamoService.listar(null, null, null, null, null, 0, 20).getTotalElements());
    }

    // ------------------------------------------------------------------

    private ReclamoRequest incidencia(String asunto) {
        ReclamoRequest r = new ReclamoRequest();
        r.setTipo(TipoReclamo.INCIDENCIA);
        r.setPrioridad(PrioridadReclamo.ALTA);
        r.setIdUsuarioResponsable(usuario.getIdUsuario());
        r.setAsunto(asunto);
        r.setDescripcion("Detalle de " + asunto);
        r.setFechaLimite(LocalDate.now().minusDays(1));
        return r;
    }

    private static CambiarEstadoReclamoRequest estado(EstadoReclamo e, String comentario) {
        CambiarEstadoReclamoRequest r = new CambiarEstadoReclamoRequest();
        r.setEstado(e);
        r.setComentario(comentario);
        return r;
    }

    private static ComentarioReclamoRequest comentario(String texto) {
        ComentarioReclamoRequest r = new ComentarioReclamoRequest();
        r.setComentario(texto);
        return r;
    }

    private static void auditar(com.genlogs.app.model.Auditable e) {
        e.setUserCreate("test");
        e.setProcessCreate("TEST");
        e.setDateCreate(LocalDateTime.now());
    }
}
