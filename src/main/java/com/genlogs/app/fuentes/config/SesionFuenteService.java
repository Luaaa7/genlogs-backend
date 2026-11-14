package com.genlogs.app.fuentes.config;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * L1 (v3.3, BPMN 9-A) · Sesión de cada tienda con su cuenta de búsqueda (D-38).
 * «Probar sesión» deja la fuente ACTIVA (sesión cifrada hasta sesion_valida_hasta),
 * REQUIERE_VERIFICACION (la tienda pidió código o CAPTCHA) o CON_ERROR. Antes de buscar,
 * {@link #sesionVigente} renueva sola la sesión vencida; si no puede, la tienda queda fuera
 * de la búsqueda sin romperla.
 */
@Service
@RequiredArgsConstructor
public class SesionFuenteService {

    /** Una sesión que vence en menos de esto se renueva antes de buscar. */
    static final Duration MARGEN_RENOVACION = Duration.ofMinutes(10);
    private static final String PROCESO = "SESION_FUENTE";
    private static final int LARGO_ERROR = 500;

    private final FuenteBusquedaConfigRepository fuenteRepository;
    private final FuenteCredencialRepository credencialRepository;
    private final CredencialesFuenteService credenciales;
    private final FuentesConfigService config;
    private final LectorSesion lector;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public FuenteConfigResponse probar(String codigoTexto) {
        CodigoFuente codigo = FuentesConfigService.codigo(codigoTexto);
        FuenteBusquedaConfig fuente = fuente(codigo);
        if (!credenciales.tiene(codigo, TipoCredencial.USUARIO) || !credenciales.tiene(codigo, TipoCredencial.CLAVE)) {
            throw new BusinessException("Primero guarda la cuenta de búsqueda (usuario y clave) de " + fuente.getNombre());
        }
        iniciar(fuente);
        return config.aRespuesta(fuente);
    }

    /** La sesión en claro para buscar en la tienda, renovada si venció. Vacía si la tienda no puede entrar. */
    @Transactional
    public Optional<String> sesionVigente(CodigoFuente codigo) {
        FuenteBusquedaConfig fuente = fuente(codigo);
        if (fuente.getEstado() != EstadoFuente.ACTIVA) {
            return Optional.empty();
        }
        LocalDateTime hasta = fuente.getSesionValidaHasta();
        if (hasta != null && hasta.isAfter(LocalDateTime.now().plus(MARGEN_RENOVACION))) {
            Optional<String> guardada = credenciales.leer(codigo, TipoCredencial.SESION);
            if (guardada.isPresent()) {
                return guardada;
            }
        }
        return iniciar(fuente) ? credenciales.leer(codigo, TipoCredencial.SESION) : Optional.empty();
    }

    /** Pide al lector iniciar sesión y deja la fuente en el estado que corresponde. true si quedó ACTIVA. */
    private boolean iniciar(FuenteBusquedaConfig fuente) {
        CodigoFuente codigo = fuente.getCodigo();
        String usuarioTienda = credenciales.leer(codigo, TipoCredencial.USUARIO).orElse(null);
        String claveTienda = credenciales.leer(codigo, TipoCredencial.CLAVE).orElse(null);
        ResultadoSesion r = (usuarioTienda == null || claveTienda == null)
                ? ResultadoSesion.error("Falta la cuenta de búsqueda de " + fuente.getNombre())
                : lector.iniciarSesion(codigo, fuente.getUrlBase(), usuarioTienda, claveTienda);
        if (r == null) {
            r = ResultadoSesion.error("El servicio de lectura no respondió.");
        }

        String nombre = usuarioActual();
        Long idUsuario = idUsuario(nombre);
        boolean ok = r.resultado() == ResultadoSesion.Resultado.OK && r.sesion() != null && r.validaHasta() != null;
        if (ok) {
            credenciales.guardar(codigo, TipoCredencial.SESION, r.sesion(), idUsuario, nombre);
            fuente.setEstado(EstadoFuente.ACTIVA);
            fuente.setSesionValidaHasta(r.validaHasta());
            fuente.setUltimoError(null);
        } else {
            credencialRepository.findByFuente_CodigoAndTipo(codigo, TipoCredencial.SESION)
                    .ifPresent(credencialRepository::delete);
            fuente.setEstado(r.resultado() == ResultadoSesion.Resultado.VERIFICACION
                    ? EstadoFuente.REQUIERE_VERIFICACION : EstadoFuente.CON_ERROR);
            fuente.setSesionValidaHasta(null);
            fuente.setUltimoError(recortar(r.mensaje() != null ? r.mensaje() : "No se pudo iniciar sesión en la tienda."));
        }
        fuente.setUltimaPrueba(LocalDateTime.now());
        fuente.setEditadoPor(idUsuario);
        fuente.setUserUpdate(nombre);
        fuente.setProcessUpdate(PROCESO);
        fuente.setDateUpdate(LocalDateTime.now());
        fuenteRepository.save(fuente);
        return ok;
    }

    private FuenteBusquedaConfig fuente(CodigoFuente codigo) {
        return fuenteRepository.findByCodigo(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la fuente " + codigo));
    }

    private static String recortar(String texto) {
        return texto.length() <= LARGO_ERROR ? texto : texto.substring(0, LARGO_ERROR);
    }

    private static String usuarioActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SISTEMA";
    }

    private Long idUsuario(String nombre) {
        return usuarioRepository.findByNombreUsuario(nombre).map(u -> u.getIdUsuario()).orElse(null);
    }
}
