package com.genlogs.app.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.AvisosResponse;
import com.genlogs.app.dto.NotificacionResponse;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Notificacion;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.NotificacionRepository;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Avisos compartidos (Paso 0, D-21 y D-25). BE-B, BE-C, BE-D y BE-E avisan con
 * este servicio sin tocar el codigo de las demas:
 *
 * <pre>
 *   // Aviso interno a todos los usuarios de un rol; con correo solo si es urgente
 *   // (D-21: alto valor y plazos vencidos).
 *   notificacionService.avisarRol(RolSistema.GERENTE_GENERAL, TipoAviso.ALTO_VALOR,
 *           "Cotizacion de alto valor", "COT-2026-15 por S/ 25 000", "/cotizaciones/15", true);
 *
 *   // Aviso a una persona (p. ej. el asistente asignado)
 *   notificacionService.avisarUsuario(idUsuario, TipoAviso.PLAZO_POR_VENCER, "...", "...", "/solicitudes/8");
 *
 *   // Cliente: solo correo por Brevo cuando cambia el estado de su pedido (D-21)
 *   notificacionService.enviarCorreoCliente(correo, nombre, "Tu pedido fue cotizado", "...");
 * </pre>
 *
 * El correo reutiliza la llave y el remitente de Brevo que ya existen (D-32). Si
 * el correo falla, el aviso interno se guarda igual.
 */
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    /** Cuantos avisos devuelve la campanita (los mas recientes). */
    static final int MAXIMO_EN_LISTA = 50;

    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final MailService mailService;

    /** Aviso interno para un usuario. */
    @Transactional
    public Notificacion avisarUsuario(Long idUsuario, TipoAviso tipo, String titulo, String mensaje, String enlace) {
        return notificacionRepository.save(nuevo(idUsuario, tipo, titulo, mensaje, enlace));
    }

    /**
     * Aviso interno para cada usuario activo del rol. Con conCorreo tambien
     * le llega por correo (D-21: solo alto valor y plazos vencidos).
     *
     * @return cuantos usuarios recibieron el aviso
     */
    @Transactional
    public int avisarRol(RolSistema rol, TipoAviso tipo, String titulo, String mensaje, String enlace, boolean conCorreo) {
        List<Usuario> destinatarios = notificacionRepository.usuariosActivosDelRol(rol.name());
        for (Usuario usuario : destinatarios) {
            Notificacion aviso = nuevo(usuario.getIdUsuario(), tipo, titulo, mensaje, enlace);
            if (conCorreo && tieneTexto(usuario.getCorreo())) {
                aviso.setCorreoEnviado(enviar(usuario.getCorreo(), usuario.getNombres(), titulo, mensaje));
            }
            notificacionRepository.save(aviso);
        }
        if (destinatarios.isEmpty()) {
            log.warn("Aviso {} sin destinatarios: no hay usuarios activos con rol {}", tipo, rol);
        }
        return destinatarios.size();
    }

    /** Correo al cliente (no tiene campanita). Devuelve false si no se pudo enviar. */
    public boolean enviarCorreoCliente(String correo, String nombre, String asunto, String mensaje) {
        if (!tieneTexto(correo)) {
            return false;
        }
        return enviar(correo, nombre, asunto, mensaje);
    }

    /** Campanita: ultimos avisos del usuario y cuantos no ha leido. */
    @Transactional(readOnly = true)
    public AvisosResponse misAvisos(String nombreUsuario) {
        Long idUsuario = idDe(nombreUsuario);
        if (idUsuario == null) {
            return new AvisosResponse(0, List.of());
        }
        List<NotificacionResponse> avisos = notificacionRepository
                .findByIdUsuarioOrderByFechaCreacionDesc(idUsuario, PageRequest.of(0, MAXIMO_EN_LISTA))
                .stream()
                .map(NotificacionService::aRespuesta)
                .toList();
        return new AvisosResponse(notificacionRepository.countByIdUsuarioAndLeidaFalse(idUsuario), avisos);
    }

    /** Marca leido un aviso propio. Un aviso de otra persona responde 404, igual que uno que no existe. */
    @Transactional
    public void marcarLeida(Long idNotificacion, String nombreUsuario) {
        Long idUsuario = idDe(nombreUsuario);
        Notificacion aviso = (idUsuario == null ? null
                : notificacionRepository.findByIdNotificacionAndIdUsuario(idNotificacion, idUsuario).orElse(null));
        if (aviso == null) {
            throw new ResourceNotFoundException("No existe el aviso " + idNotificacion);
        }
        if (!Boolean.TRUE.equals(aviso.getLeida())) {
            aviso.setLeida(true);
            aviso.setFechaLectura(LocalDateTime.now());
            notificacionRepository.save(aviso);
        }
    }

    // ------------------------------------------------------------------------

    private static Notificacion nuevo(Long idUsuario, TipoAviso tipo, String titulo, String mensaje, String enlace) {
        if (idUsuario == null || tipo == null || !tieneTexto(titulo)) {
            throw new IllegalArgumentException("El aviso necesita usuario, tipo y título");
        }
        Notificacion aviso = new Notificacion();
        aviso.setIdUsuario(idUsuario);
        aviso.setTipo(tipo.name());
        aviso.setTitulo(recortar(titulo.trim(), 150));
        aviso.setMensaje(mensaje);
        aviso.setEnlace(enlace == null ? null : recortar(enlace, 255));
        return aviso;
    }

    private Long idDe(String nombreUsuario) {
        if (nombreUsuario == null) {
            return null;
        }
        return usuarioRepository.findByNombreUsuario(nombreUsuario).map(Usuario::getIdUsuario).orElse(null);
    }

    private boolean enviar(String correo, String nombre, String asunto, String mensaje) {
        try {
            String texto = mensaje == null ? asunto : mensaje;
            String html = "<p>" + escaparHtml(texto).replace("\n", "<br>") + "</p>"
                    + "<p style=\"color:#6b7280;font-size:12px\">Aviso automático de GenLogs.</p>";
            return mailService.enviarCorreo(correo, nombre, asunto, html, texto);
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar el correo del aviso a {}: {}", correo, e.getMessage());
            return false;
        }
    }

    private static NotificacionResponse aRespuesta(Notificacion n) {
        return new NotificacionResponse(n.getIdNotificacion(), n.getTipo(), n.getTitulo(), n.getMensaje(),
                n.getEnlace(), Boolean.TRUE.equals(n.getLeida()), n.getFechaCreacion());
    }

    private static boolean tieneTexto(String s) {
        return s != null && !s.isBlank();
    }

    private static String recortar(String s, int maximo) {
        return s.length() <= maximo ? s : s.substring(0, maximo);
    }

    private static String escaparHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}