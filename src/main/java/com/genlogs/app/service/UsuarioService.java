package com.genlogs.app.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.UsuarioRequest;
import com.genlogs.app.dto.UsuarioResponse;
import com.genlogs.app.dto.UsuarioUpdateRequest; // Asegúrate de tener este DTO importado si lo requiere
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.PasswordResetTokenRepository;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.SeguimientoCotizacionRepository;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    /**
     * Un usuario CLIENTE nace solo del registro del portal con RUC, que lo une a su empresa.
     * Creado desde «Usuarios» quedaría sin empresa y vería el portal vacío.
     */
    static final String SOLO_REGISTRO_PORTAL =
            "Los usuarios del portal se crean solo con el registro por RUC. Elige un rol del personal.";

    private static boolean esCliente(Rol rol) {
        return rol != null && RolSistema.CLIENTE.name().equalsIgnoreCase(rol.getNombreRol());
    }

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final CotizacionRepository cotizacionRepository;
    private final SeguimientoCotizacionRepository seguimientoRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final NotificacionService notificacionService;

    /** Usuarios del portal que pidieron acceso a una empresa que ya es cliente y esperan activación. */
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarPendientesPortal() {
        return usuarioRepository.findByStatusAndProcessCreate("I", PortalClienteService.PROCESO_PENDIENTE).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * El Jefe Administrativo activa a un usuario pendiente del portal. Solo aplica a los
     * que quedaron pendientes al registrarse: un usuario dado de baja no se reactiva aquí.
     * Al cliente se le avisa por correo; si el correo falla, el usuario queda activo igual.
     */
    @Transactional
    public UsuarioResponse activarPendientePortal(Long idUsuario) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .filter(u -> "I".equals(u.getStatus() == null ? null : u.getStatus().trim()))
                .filter(u -> PortalClienteService.PROCESO_PENDIENTE.equals(u.getProcessCreate()))
                .orElseThrow(() -> new ResourceNotFoundException("No hay un acceso pendiente con ese usuario"));
        usuario.setStatus("A");
        usuario.setIntentosFallidos((short) 0);
        usuario.setUserUpdate(usuarioActual());
        usuario.setProcessUpdate("ACTIVAR_ACCESO_PORTAL");
        usuario.setDateUpdate(LocalDateTime.now());
        usuario = usuarioRepository.save(usuario);
        try {
            notificacionService.enviarCorreoCliente(usuario.getCorreo(), usuario.getNombres(),
                    "Tu acceso al portal de GENLOGS está habilitado",
                    "Ya puedes ingresar al portal de GENLOGS con tu correo y contraseña.");
        } catch (RuntimeException ex) {
            // El acceso ya quedó activo; el correo es solo un aviso.
        }
        return toResponse(usuario);
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findByStatus("A").stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UsuarioResponse crear(UsuarioRequest request) {
        if (usuarioRepository.existsByNombreUsuario(request.getNombreUsuario())) {
            throw new BusinessException("Ya existe un usuario con ese nombre de usuario");
        }
        if (usuarioRepository.existsByCorreo(request.getCorreo())) {
            throw new BusinessException("Ya existe un usuario con ese correo");
        }

        Rol rol = rolRepository.findById(request.getIdRol())
                .orElseThrow(() -> new ResourceNotFoundException("El rol indicado no existe"));
        if (esCliente(rol)) {
            throw new BusinessException(SOLO_REGISTRO_PORTAL);
        }

        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        usuario.setNombreUsuario(request.getNombreUsuario());
        usuario.setNombres(request.getNombres());
        usuario.setCorreo(request.getCorreo());
        usuario.setIniciales(request.getIniciales());
        usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        usuario.setBloqueado(false);
        usuario.setIntentosFallidos((short) 0);
        usuario.setUserCreate(usuarioActual());
        usuario.setProcessCreate("ALTA_USUARIO");

        usuario = usuarioRepository.save(usuario);
        return toResponse(usuario);
    }

    @Transactional
    public UsuarioResponse cambiarBloqueo(Long idUsuario, boolean bloqueado) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        usuario.setBloqueado(bloqueado);
        if (!bloqueado) {
            usuario.setIntentosFallidos((short) 0); // desbloquear resetea el contador
        }
        usuario.setUserUpdate(usuarioActual());
        usuario.setProcessUpdate("CAMBIO_BLOQUEO_USUARIO");
        usuario.setDateUpdate(LocalDateTime.now());

        usuario = usuarioRepository.save(usuario);
        return toResponse(usuario);
    }

    // --- MÉTODOS NUEVOS AGREGADOS ---

    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long idUsuario) {
        return toResponse(obtenerActivo(idUsuario));
    }

    @Transactional
    public UsuarioResponse actualizar(Long idUsuario, UsuarioUpdateRequest request) {
        Usuario usuario = obtenerActivo(idUsuario);

        if (usuarioRepository.existsByCorreoIgnoreCaseAndIdUsuarioNot(request.getCorreo(), idUsuario)) {
            throw new BusinessException("Ya existe un usuario con ese correo");
        }
        Rol rol = rolRepository.findById(request.getIdRol())
                .orElseThrow(() -> new ResourceNotFoundException("El rol indicado no existe"));
        if (esCliente(rol) && !esCliente(usuario.getRol())) {
            throw new BusinessException(SOLO_REGISTRO_PORTAL);
        }

        usuario.setRol(rol);
        usuario.setNombres(request.getNombres());
        usuario.setCorreo(request.getCorreo());
        usuario.setIniciales(request.getIniciales());
        usuario.setUserUpdate(usuarioActual());
        usuario.setProcessUpdate("EDICION_USUARIO");
        usuario.setDateUpdate(LocalDateTime.now());
        return toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public void desactivar(Long idUsuario) {
        Usuario usuario = obtenerActivo(idUsuario);
        if (usuario.getNombreUsuario().equals(usuarioActual())) {
            throw new BusinessException("No puedes desactivar tu propio usuario");
        }
        // Borrado inteligente: si el usuario nunca participó en cotizaciones ni en su
        // historial, se elimina de verdad (queda libre su nombre de usuario y correo).
        // Si tiene historial se conserva y solo se desactiva (baja lógica).
        boolean tieneHistorial = cotizacionRepository.existsByUsuarioVendedor_IdUsuario(idUsuario)
                || seguimientoRepository.existsByUsuario_IdUsuario(idUsuario);

        if (!tieneHistorial) {
            resetTokenRepository.deleteByUsuario_IdUsuario(idUsuario);
            resetTokenRepository.flush();
            usuarioRepository.delete(usuario);
            return;
        }

        usuario.setStatus("I");
        usuario.setUserUpdate(usuarioActual());
        usuario.setProcessUpdate("BAJA_USUARIO");
        usuario.setDateUpdate(LocalDateTime.now());
        usuarioRepository.save(usuario);
    }

    private Usuario obtenerActivo(Long idUsuario) {
        return usuarioRepository.findByIdUsuarioAndStatus(idUsuario, "A")
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    // ------------------------------------------------------------------

    private UsuarioResponse toResponse(Usuario usuario) {
        return UsuarioResponse.builder()
                .idUsuario(usuario.getIdUsuario())
                .nombreUsuario(usuario.getNombreUsuario())
                .nombres(usuario.getNombres())
                .correo(usuario.getCorreo())
                .nombreRol(usuario.getRol().getNombreRol())
                .idRol(usuario.getRol().getIdRol())
                .iniciales(usuario.getIniciales())
                .bloqueado(usuario.getBloqueado())
                .build();
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}