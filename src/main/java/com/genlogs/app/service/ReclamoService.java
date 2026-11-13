package com.genlogs.app.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.CalificacionReclamoRequest;
import com.genlogs.app.dto.CambiarEstadoReclamoRequest;
import com.genlogs.app.dto.ComentarioReclamoRequest;
import com.genlogs.app.dto.PaginaResponse;
import com.genlogs.app.dto.ReclamoPortalRequest;
import com.genlogs.app.dto.ReclamoPortalResponse;
import com.genlogs.app.dto.ReclamoRequest;
import com.genlogs.app.dto.ReclamoResponse;
import com.genlogs.app.dto.SeguimientoReclamoResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.EstadoReclamo;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.model.PrioridadReclamo;
import com.genlogs.app.model.Reclamo;
import com.genlogs.app.model.SeguimientoReclamo;
import com.genlogs.app.model.TipoReclamo;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.OrdenCompraRepository;
import com.genlogs.app.repository.ReclamoRepository;
import com.genlogs.app.repository.SeguimientoReclamoRepository;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Módulo de reclamos e incidencias: registro, asignación de responsable,
 * flujo de estados y bitácora de seguimiento.
 */
@Service
@RequiredArgsConstructor
public class ReclamoService {

    private static final Pattern FORMATO_CODIGO = Pattern.compile("^[A-Z]{3}-(\\d+)$");

    private final ReclamoRepository reclamoRepository;
    private final SeguimientoReclamoRepository seguimientoRepository;
    private final ClienteRepository clienteRepository;
    private final OrdenCompraRepository ordenCompraRepository;
    private final UsuarioRepository usuarioRepository;

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public PaginaResponse<ReclamoResponse> listar(TipoReclamo tipo, EstadoReclamo estado,
                                                  PrioridadReclamo prioridad, Long idCliente,
                                                  Long idResponsable, int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 200);
        Page<Reclamo> resultado = reclamoRepository.buscar(
                tipo, estado, prioridad, idCliente, idResponsable, PageRequest.of(p, s));
        return PaginaResponse.of(resultado.map(r -> toResponse(r, false)));
    }

    @Transactional(readOnly = true)
    public ReclamoResponse buscarPorId(Long idReclamo) {
        return toResponse(obtenerOLanzar(idReclamo), true);
    }

    @Transactional(readOnly = true)
    public List<SeguimientoReclamoResponse> listarSeguimientos(Long idReclamo) {
        obtenerOLanzar(idReclamo);
        return seguimientos(idReclamo);
    }

    /** Usuarios activos que pueden ser responsables (para el selector del frontend). */
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> listarResponsables() {
        return usuarioRepository.findAll().stream()
                .filter(u -> "A".equals(u.getStatus()) && !Boolean.TRUE.equals(u.getBloqueado()))
                .sorted(java.util.Comparator.comparing(Usuario::getNombres, String.CASE_INSENSITIVE_ORDER))
                .map(u -> java.util.Map.<String, Object>of("idUsuario", u.getIdUsuario(), "nombres", u.getNombres()))
                .toList();
    }

    // ------------------------------------------------------------------ altas y cambios

    @Transactional
    public ReclamoResponse crear(ReclamoRequest request) {
        Usuario usuario = usuarioActual();

        Reclamo reclamo = Reclamo.builder()
                .tipo(request.getTipo())
                .estado(EstadoReclamo.REGISTRADO)
                .fechaRegistro(LocalDateTime.now())
                .codigoReclamo(siguienteCodigo(request.getTipo()))
                .build();
        aplicarDatos(reclamo, request);
        reclamo.setUserCreate(usuario.getNombreUsuario());
        reclamo.setProcessCreate("ALTA_RECLAMO");
        reclamo = reclamoRepository.save(reclamo);

        registrarSeguimiento(reclamo, null, EstadoReclamo.REGISTRADO,
                "Registrado: " + reclamo.getAsunto(), usuario, "ALTA_RECLAMO");
        return toResponse(reclamo, true);
    }

    @Transactional
    public ReclamoResponse actualizar(Long idReclamo, ReclamoRequest request) {
        Reclamo reclamo = obtenerOLanzar(idReclamo);
        if (reclamo.getEstado().esFinal()) {
            throw new BusinessException("El " + reclamo.getTipo().name().toLowerCase()
                    + " está " + reclamo.getEstado().name().toLowerCase() + " y ya no se puede editar.");
        }
        if (request.getTipo() != reclamo.getTipo()) {
            throw new BusinessException("No se puede cambiar el tipo de un registro existente.");
        }

        Usuario anteriorResponsable = reclamo.getResponsable();
        aplicarDatos(reclamo, request);

        Usuario usuario = usuarioActual();
        marcarActualizacion(reclamo, usuario, "EDICION_RECLAMO");
        reclamo = reclamoRepository.save(reclamo);

        // Dejar constancia del cambio de responsable en la bitácora.
        Usuario nuevoResponsable = reclamo.getResponsable();
        Long idAnterior = anteriorResponsable == null ? null : anteriorResponsable.getIdUsuario();
        Long idNuevo = nuevoResponsable == null ? null : nuevoResponsable.getIdUsuario();
        if (!java.util.Objects.equals(idAnterior, idNuevo)) {
            String texto = nuevoResponsable == null
                    ? "Se quitó el responsable."
                    : "Responsable asignado: " + nuevoResponsable.getNombres();
            registrarSeguimiento(reclamo, reclamo.getEstado(), reclamo.getEstado(),
                    texto, usuario, "ASIGNAR_RESPONSABLE_RECLAMO");
        }
        return toResponse(reclamo, true);
    }

    @Transactional
    public ReclamoResponse cambiarEstado(Long idReclamo, CambiarEstadoReclamoRequest request) {
        Reclamo reclamo = obtenerOLanzar(idReclamo);
        EstadoReclamo actual = reclamo.getEstado();
        EstadoReclamo nuevo = request.getEstado();
        String comentario = request.getComentario() == null ? null : request.getComentario().trim();

        if (actual == nuevo) {
            throw new BusinessException("El registro ya está en estado " + nuevo + ".");
        }
        if (!actual.puedePasarA(nuevo)) {
            throw new BusinessException("No se puede pasar de " + actual + " a " + nuevo
                    + ". Estados permitidos: " + actual.siguientesPermitidos());
        }
        if ((nuevo == EstadoReclamo.RESUELTO || nuevo == EstadoReclamo.ANULADO)
                && (comentario == null || comentario.isEmpty())) {
            throw new BusinessException(nuevo == EstadoReclamo.RESUELTO
                    ? "Debe describir la solución para marcarlo como RESUELTO."
                    : "Debe indicar el motivo de la anulación.");
        }

        reclamo.setEstado(nuevo);
        switch (nuevo) {
            case RESUELTO -> reclamo.setSolucion(comentario);
            case CERRADO, ANULADO -> reclamo.setFechaCierre(LocalDateTime.now());
            case EN_PROCESO -> reclamo.setFechaCierre(null); // incluye reapertura desde RESUELTO
            default -> { }
        }

        Usuario usuario = usuarioActual();
        marcarActualizacion(reclamo, usuario, "CAMBIO_ESTADO_RECLAMO");
        reclamo = reclamoRepository.save(reclamo);

        registrarSeguimiento(reclamo, actual, nuevo, comentario, usuario, "CAMBIO_ESTADO_RECLAMO");
        return toResponse(reclamo, true);
    }

    @Transactional
    public SeguimientoReclamoResponse agregarComentario(Long idReclamo, ComentarioReclamoRequest request) {
        Reclamo reclamo = obtenerOLanzar(idReclamo);
        if (reclamo.getEstado().esFinal()) {
            throw new BusinessException("No se pueden agregar comentarios a un registro "
                    + reclamo.getEstado().name().toLowerCase() + ".");
        }
        SeguimientoReclamo s = registrarSeguimiento(reclamo, reclamo.getEstado(), reclamo.getEstado(),
                request.getComentario().trim(), usuarioActual(), "COMENTARIO_RECLAMO");
        return toSeguimientoResponse(s);
    }

    /** Eliminación lógica (status = 'I'). */
    @Transactional
    public void eliminar(Long idReclamo) {
        Reclamo reclamo = obtenerOLanzar(idReclamo);
        reclamo.setStatus("I");
        marcarActualizacion(reclamo, usuarioActual(), "BAJA_RECLAMO");
        reclamoRepository.save(reclamo);
    }

    // ------------------------------------------------------------------ portal del cliente (RF-70)

    /** Reclamos de la empresa del cliente que inició sesión; nunca los de otra empresa. */
    @Transactional(readOnly = true)
    public List<ReclamoPortalResponse> listarDelPortal() {
        Usuario usuario = clienteDelPortal();
        return reclamoRepository
                .findByCliente_IdClienteAndStatusOrderByFechaRegistroDescIdReclamoDesc(
                        usuario.getCliente().getIdCliente(), "A")
                .stream()
                .map(r -> toPortalResponse(r, false))
                .toList();
    }

    /** Detalle con el historial de estados. Un reclamo de otra empresa responde 404, igual que uno inexistente. */
    @Transactional(readOnly = true)
    public ReclamoPortalResponse detalleDelPortal(Long idReclamo) {
        return toPortalResponse(reclamoDelPortal(idReclamo, clienteDelPortal()), true);
    }

    @Transactional
    public ReclamoPortalResponse crearDesdePortal(ReclamoPortalRequest request) {
        Usuario usuario = clienteDelPortal();
        Cliente cliente = usuario.getCliente();

        OrdenCompra orden = null;
        if (request.getIdOrdenCompra() != null) {
            // Una OC de otra empresa se trata como inexistente, para no revelar que existe.
            orden = ordenCompraRepository.findById(request.getIdOrdenCompra())
                    .filter(o -> "A".equals(o.getStatus()))
                    .filter(o -> o.getCotizacion() != null && o.getCotizacion().getCliente() != null
                            && cliente.getIdCliente().equals(o.getCotizacion().getCliente().getIdCliente()))
                    .orElseThrow(() -> new ResourceNotFoundException("Orden de compra no encontrada"));
        }

        Reclamo reclamo = Reclamo.builder()
                .tipo(TipoReclamo.RECLAMO)
                .estado(EstadoReclamo.REGISTRADO)
                .prioridad(PrioridadReclamo.MEDIA)
                .cliente(cliente)
                .ordenCompra(orden)
                .asunto(request.getAsunto().trim())
                .descripcion(request.getDescripcion().trim())
                .fechaRegistro(LocalDateTime.now())
                .codigoReclamo(siguienteCodigo(TipoReclamo.RECLAMO))
                .build();
        reclamo.setUserCreate(usuario.getNombreUsuario());
        reclamo.setProcessCreate("ALTA_RECLAMO_PORTAL");
        reclamo = reclamoRepository.save(reclamo);

        registrarSeguimiento(reclamo, null, EstadoReclamo.REGISTRADO,
                "Registrado por el cliente desde el portal: " + reclamo.getAsunto(), usuario, "ALTA_RECLAMO_PORTAL");
        return toPortalResponse(reclamo, true);
    }

    /** El cliente califica la atención una sola vez, cuando el reclamo ya está RESUELTO o CERRADO. */
    @Transactional
    public ReclamoPortalResponse calificarDesdePortal(Long idReclamo, CalificacionReclamoRequest request) {
        Usuario usuario = clienteDelPortal();
        Reclamo reclamo = reclamoDelPortal(idReclamo, usuario);
        if (reclamo.getCalificacion() != null) {
            throw new BusinessException("Este reclamo ya fue calificado.");
        }
        if (!sePuedeCalificar(reclamo)) {
            throw new BusinessException("Podrás calificar la atención cuando el reclamo esté resuelto.");
        }
        String comentario = request.getComentario() == null || request.getComentario().isBlank()
                ? null : request.getComentario().trim();

        reclamo.setCalificacion(request.getPuntaje().shortValue());
        reclamo.setComentarioCalificacion(comentario);
        reclamo.setFechaCalificacion(LocalDateTime.now());
        marcarActualizacion(reclamo, usuario, "CALIFICACION_RECLAMO");
        reclamo = reclamoRepository.save(reclamo);

        registrarSeguimiento(reclamo, reclamo.getEstado(), reclamo.getEstado(),
                "Calificación del cliente: " + request.getPuntaje() + "/5"
                        + (comentario == null ? "" : ". " + comentario),
                usuario, "CALIFICACION_RECLAMO");
        return toPortalResponse(reclamo, true);
    }

    /** Usuario CLIENTE con empresa; si no la tiene, no hay portal (403). */
    private Usuario clienteDelPortal() {
        String nombreUsuario = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findWithClienteByNombreUsuario(nombreUsuario).orElse(null);
        if (usuario == null || usuario.getCliente() == null) {
            throw new AccessDeniedException("El acceso del portal todavía no está habilitado");
        }
        return usuario;
    }

    private Reclamo reclamoDelPortal(Long idReclamo, Usuario usuario) {
        return reclamoRepository
                .findByIdReclamoAndCliente_IdClienteAndStatus(idReclamo, usuario.getCliente().getIdCliente(), "A")
                .orElseThrow(() -> new ResourceNotFoundException("Reclamo no encontrado"));
    }

    private static boolean sePuedeCalificar(Reclamo r) {
        return r.getCalificacion() == null
                && (r.getEstado() == EstadoReclamo.RESUELTO || r.getEstado() == EstadoReclamo.CERRADO);
    }

    private ReclamoPortalResponse toPortalResponse(Reclamo r, boolean conHistorial) {
        List<ReclamoPortalResponse.Evento> historial = null;
        if (conHistorial && r.getIdReclamo() != null) {
            // Solo los cambios de estado: los comentarios del personal son internos.
            historial = seguimientoRepository
                    .findByReclamo_IdReclamoAndStatusOrderByFechaEventoAscIdSeguimientoReclamoAsc(r.getIdReclamo(), "A")
                    .stream()
                    .filter(s -> s.getEstadoAnterior() == null || s.getEstadoAnterior() != s.getEstadoNuevo())
                    .map(s -> new ReclamoPortalResponse.Evento(s.getEstadoNuevo().name(), s.getFechaEvento()))
                    .toList();
        }
        return ReclamoPortalResponse.builder()
                .idReclamo(r.getIdReclamo())
                .codigoReclamo(r.getCodigoReclamo())
                .estado(r.getEstado().name())
                .asunto(r.getAsunto())
                .descripcion(r.getDescripcion())
                .numeroOrdenCompra(r.getOrdenCompra() == null ? null : r.getOrdenCompra().getNumeroOrdenCompra())
                .fechaRegistro(r.getFechaRegistro())
                .fechaCierre(r.getFechaCierre())
                .solucion(r.getSolucion())
                .calificacion(r.getCalificacion() == null ? null : r.getCalificacion().intValue())
                .comentarioCalificacion(r.getComentarioCalificacion())
                .puedeCalificar(sePuedeCalificar(r))
                .historial(historial)
                .build();
    }

    // ------------------------------------------------------------------ helpers

    /** Copia los datos editables del request a la entidad, validando referencias. */
    private void aplicarDatos(Reclamo reclamo, ReclamoRequest request) {
        OrdenCompra orden = null;
        if (request.getIdOrdenCompra() != null) {
            orden = ordenCompraRepository.findById(request.getIdOrdenCompra())
                    .filter(o -> "A".equals(o.getStatus()))
                    .orElseThrow(() -> new ResourceNotFoundException("Orden de compra no encontrada"));
        }

        Cliente cliente = null;
        if (request.getIdCliente() != null) {
            cliente = clienteRepository.findById(request.getIdCliente())
                    .filter(c -> "A".equals(c.getStatus()))
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
        } else if (orden != null) {
            cliente = orden.getCotizacion().getCliente();
        }

        if (orden != null && cliente != null
                && !orden.getCotizacion().getCliente().getIdCliente().equals(cliente.getIdCliente())) {
            throw new BusinessException("La orden de compra no pertenece al cliente indicado.");
        }
        if (request.getTipo() == TipoReclamo.RECLAMO && cliente == null) {
            throw new BusinessException("Un reclamo debe estar asociado a un cliente.");
        }

        Usuario responsable = null;
        if (request.getIdUsuarioResponsable() != null) {
            responsable = usuarioRepository.findByIdUsuarioAndStatus(request.getIdUsuarioResponsable(), "A")
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario responsable no encontrado"));
        }

        reclamo.setCliente(cliente);
        reclamo.setOrdenCompra(orden);
        reclamo.setResponsable(responsable);
        reclamo.setPrioridad(request.getPrioridad() != null ? request.getPrioridad() : PrioridadReclamo.MEDIA);
        reclamo.setAsunto(request.getAsunto().trim());
        reclamo.setDescripcion(request.getDescripcion().trim());
        reclamo.setFechaLimite(request.getFechaLimite());
    }

    private SeguimientoReclamo registrarSeguimiento(Reclamo reclamo, EstadoReclamo anterior,
                                                    EstadoReclamo nuevo, String comentario,
                                                    Usuario usuario, String proceso) {
        SeguimientoReclamo s = SeguimientoReclamo.builder()
                .reclamo(reclamo)
                .estadoAnterior(anterior)
                .estadoNuevo(nuevo)
                .usuario(usuario)
                .fechaEvento(LocalDateTime.now())
                .comentario(comentario)
                .build();
        s.setUserCreate(usuario.getNombreUsuario());
        s.setProcessCreate(proceso);
        return seguimientoRepository.save(s);
    }

    /** REC-000001 para reclamos, INC-000001 para incidencias. */
    private String siguienteCodigo(TipoReclamo tipo) {
        String prefijo = tipo == TipoReclamo.RECLAMO ? "REC" : "INC";
        int mayor = reclamoRepository.codigosPorTipo(tipo).stream()
                .map(c -> c == null ? null : FORMATO_CODIGO.matcher(c.trim()))
                .filter(m -> m != null && m.matches())
                .mapToInt(m -> Integer.parseInt(m.group(1)))
                .max()
                .orElse(0);
        String codigo;
        do {
            mayor++;
            codigo = String.format("%s-%06d", prefijo, mayor);
        } while (reclamoRepository.existsByCodigoReclamo(codigo));
        return codigo;
    }

    private void marcarActualizacion(Reclamo reclamo, Usuario usuario, String proceso) {
        reclamo.setUserUpdate(usuario.getNombreUsuario());
        reclamo.setProcessUpdate(proceso);
        reclamo.setDateUpdate(LocalDateTime.now());
    }

    private Reclamo obtenerOLanzar(Long idReclamo) {
        return reclamoRepository.findById(idReclamo)
                .filter(r -> "A".equals(r.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Reclamo o incidencia no encontrado"));
    }

    private Usuario usuarioActual() {
        String nombreUsuario = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));
    }

    private List<SeguimientoReclamoResponse> seguimientos(Long idReclamo) {
        return seguimientoRepository
                .findByReclamo_IdReclamoAndStatusOrderByFechaEventoAscIdSeguimientoReclamoAsc(idReclamo, "A")
                .stream()
                .map(this::toSeguimientoResponse)
                .toList();
    }

    private SeguimientoReclamoResponse toSeguimientoResponse(SeguimientoReclamo s) {
        return SeguimientoReclamoResponse.builder()
                .idSeguimientoReclamo(s.getIdSeguimientoReclamo())
                .estadoAnterior(s.getEstadoAnterior() == null ? null : s.getEstadoAnterior().name())
                .estadoNuevo(s.getEstadoNuevo().name())
                .idUsuario(s.getUsuario() == null ? null : s.getUsuario().getIdUsuario())
                .usuario(s.getUsuario() == null ? null : s.getUsuario().getNombres())
                .fechaEvento(s.getFechaEvento())
                .comentario(s.getComentario())
                .build();
    }

    private ReclamoResponse toResponse(Reclamo r, boolean conSeguimientos) {
        boolean abierto = r.getEstado() == EstadoReclamo.REGISTRADO || r.getEstado() == EstadoReclamo.EN_PROCESO;
        boolean vencido = abierto && r.getFechaLimite() != null && r.getFechaLimite().isBefore(LocalDate.now());

        return ReclamoResponse.builder()
                .idReclamo(r.getIdReclamo())
                .codigoReclamo(r.getCodigoReclamo())
                .tipo(r.getTipo().name())
                .estado(r.getEstado().name())
                .prioridad(r.getPrioridad().name())
                .idCliente(r.getCliente() == null ? null : r.getCliente().getIdCliente())
                .cliente(r.getCliente() == null ? null : r.getCliente().getTercero().getRazonSocial())
                .idOrdenCompra(r.getOrdenCompra() == null ? null : r.getOrdenCompra().getIdOrdenCompra())
                .numeroOrdenCompra(r.getOrdenCompra() == null ? null : r.getOrdenCompra().getNumeroOrdenCompra())
                .idUsuarioResponsable(r.getResponsable() == null ? null : r.getResponsable().getIdUsuario())
                .responsable(r.getResponsable() == null ? null : r.getResponsable().getNombres())
                .asunto(r.getAsunto())
                .descripcion(r.getDescripcion())
                .fechaRegistro(r.getFechaRegistro())
                .fechaLimite(r.getFechaLimite())
                .fechaCierre(r.getFechaCierre())
                .solucion(r.getSolucion())
                .calificacion(r.getCalificacion() == null ? null : r.getCalificacion().intValue())
                .comentarioCalificacion(r.getComentarioCalificacion())
                .vencido(vencido)
                .siguientesEstados(r.getEstado().siguientesPermitidos().stream().map(Enum::name).toList())
                .seguimientos(conSeguimientos && r.getIdReclamo() != null ? seguimientos(r.getIdReclamo()) : null)
                .build();
    }
}
