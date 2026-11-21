package com.genlogs.app.service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.IngresoGoogleResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.security.google.CuentaGoogle;
import com.genlogs.app.security.google.VerificadorTokenGoogle;

import lombok.RequiredArgsConstructor;

/**
 * Ingreso del cliente con Google desde la landing y el portal (v3.4).
 * <ul>
 *   <li>Cuenta ya vinculada (google_sub) o cliente con ese correo: entra con su usuario; si no
 *       estaba vinculada, se vincula (Google ya verificó que el correo es suyo).</li>
 *   <li>Correo nuevo: se crea un usuario CLIENTE sin empresa. Antes de su primera cotización
 *       el portal le pide el RUC (PortalClienteService.completarEmpresa).</li>
 *   <li>El personal interno no entra por aquí: usa el acceso del personal.</li>
 * </ul>
 * El ingreso con correo y contraseña sigue igual.
 */
@Service
@RequiredArgsConstructor
public class IngresoGoogleService {

    private static final Logger log = LoggerFactory.getLogger(IngresoGoogleService.class);
    private static final String ROL_CLIENTE = "CLIENTE";
    static final String PROCESO_GOOGLE = "REGISTRO_GOOGLE";

    private final VerificadorTokenGoogle verificador;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final PortalClienteService portalClienteService;

    @Transactional
    public IngresoGoogleResponse ingresar(String credential) {
        CuentaGoogle cuenta = verificador.verificar(credential);

        Usuario usuario = usuarioRepository.findWithRolByGoogleSub(cuenta.sub())
                .or(() -> usuarioRepository.findWithRolByCorreoIgnoreCase(cuenta.correo()))
                .orElse(null);
        if (usuario == null) {
            usuario = crear(cuenta);
        } else {
            if (!ROL_CLIENTE.equalsIgnoreCase(usuario.getRol().getNombreRol())) {
                throw new BusinessException(
                        "Este correo pertenece al personal de GENLOGS. Ingresa desde el acceso del personal.");
            }
            if (usuario.getGoogleSub() != null && !usuario.getGoogleSub().equals(cuenta.sub())) {
                log.warn("Ingreso con Google rechazado: el usuario {} está vinculado a otra cuenta de Google", usuario.getIdUsuario());
                throw new BusinessException("Este correo ya está vinculado a otra cuenta de Google.");
            }
            String status = usuario.getStatus() == null ? "" : usuario.getStatus().trim();
            if (status.startsWith("I")) {
                if (PortalClienteService.PROCESO_PENDIENTE.equals(usuario.getProcessCreate())
                        || PortalClienteService.PROCESO_PENDIENTE.equals(usuario.getProcessUpdate())) {
                    return IngresoGoogleResponse.pendiente(usuario.getNombres());
                }
                throw new BusinessException("El usuario está inactivo. Contacta al administrador.");
            }
            if (Boolean.TRUE.equals(usuario.getBloqueado())) {
                throw new BusinessException("El usuario está bloqueado. Contacta al administrador.");
            }
            if (usuario.getGoogleSub() == null) {
                usuario.setGoogleSub(cuenta.sub());
                usuario.setUserUpdate(usuario.getNombreUsuario());
                usuario.setProcessUpdate("VINCULAR_GOOGLE");
                usuario.setDateUpdate(LocalDateTime.now());
                usuario = usuarioRepository.save(usuario);
            }
        }
        return IngresoGoogleResponse.conAcceso(authService.emitirToken(usuario), usuario.getNombreUsuario(),
                usuario.getNombres(), usuario.getRol().getNombreRol(), usuario.getCliente() != null);
    }

    private Usuario crear(CuentaGoogle cuenta) {
        Rol rol = rolRepository.findByNombreRolIgnoreCase(ROL_CLIENTE).orElseThrow(() -> {
            log.error("Ingreso con Google rechazado: no existe el rol CLIENTE. Ejecuta la migración.");
            return new BusinessException("El ingreso no está disponible en este momento. Inténtalo más tarde.");
        });
        String nombreUsuario = portalClienteService.nombreUsuarioDisponible(cuenta.correo());
        if (nombreUsuario == null) {
            throw new BusinessException("No pudimos crear tu usuario. Inténtalo de nuevo en unos minutos.");
        }
        String nombres = cuenta.nombre() != null ? cuenta.nombre() : parteLocal(cuenta.correo());
        if (nombres.length() > 150) {
            nombres = nombres.substring(0, 150);
        }

        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        usuario.setNombreUsuario(nombreUsuario);
        usuario.setNombres(nombres);
        usuario.setCorreo(cuenta.correo());
        usuario.setGoogleSub(cuenta.sub());
        usuario.setIniciales(portalClienteService.iniciales(nombres, parteLocal(cuenta.correo())));
        // Entra con Google: la contraseña es aleatoria y no se comunica. Puede crear una con «¿Olvidaste tu contraseña?».
        usuario.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        usuario.setBloqueado(false);
        usuario.setIntentosFallidos((short) 0);
        usuario.setStatus("A");
        usuario.setUserCreate("portal");
        usuario.setProcessCreate(PROCESO_GOOGLE);
        usuario.setDateCreate(LocalDateTime.now());
        log.info("Cliente nuevo con Google: usuario {}", nombreUsuario);
        return usuarioRepository.saveAndFlush(usuario);
    }

    private static String parteLocal(String correo) {
        int arroba = correo.indexOf('@');
        String parte = arroba > 0 ? correo.substring(0, arroba) : correo;
        return parte.replace('.', ' ').replace('_', ' ').trim().toLowerCase(Locale.ROOT);
    }
}
