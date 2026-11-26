package com.genlogs.app.fuentes.config;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.DemasiadasSolicitudesException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * L1 (v3.3, BPMN 9-A) · Sesión de cada tienda con su cuenta de búsqueda (D-38).
 * «Probar sesión» deja la fuente ACTIVA (sesión cifrada hasta sesion_valida_hasta),
 * REQUIERE_VERIFICACION (la tienda pidió código o CAPTCHA) o CON_ERROR. Si al probar faltan
 * las reglas de ingreso, queda SIN_PROBAR; al renovar (D-41) queda CON_ERROR. Antes de buscar,
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
    private final FuenteSelectorRepository selectorRepository;
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
        iniciar(fuente, true);
        return config.aRespuesta(fuente);
    }

    /** La sesión en claro para buscar en la tienda, renovada si venció. Vacía si la tienda no puede entrar. */
    @Transactional
    public Optional<String> sesionVigente(CodigoFuente codigo) {
        FuenteBusquedaConfig fuente = fuente(codigo);
        if (fuente.getEstado() != EstadoFuente.ACTIVA) {
            return Optional.empty();
        }
        if (!configuracionCompleta(codigo)) {
            aplicar(fuente, false, ResultadoSesion.sinConfigurar("Fuente omitida: sin configurar."));
            return Optional.empty();
        }
        LocalDateTime hasta = fuente.getSesionValidaHasta();
        if (hasta != null && hasta.isAfter(LocalDateTime.now().plus(MARGEN_RENOVACION))) {
            Optional<String> guardada = credenciales.leer(codigo, TipoCredencial.SESION);
            if (guardada.isPresent()) {
                return guardada;
            }
        }
        return iniciar(fuente, false) ? credenciales.leer(codigo, TipoCredencial.SESION) : Optional.empty();
    }

    /**
     * 9-B.1 (tarea F): la tienda pidió código o CAPTCHA mientras se leía. La fuente queda
     * REQUIERE_VERIFICACION, sin sesión y fuera de la búsqueda hasta que se vuelva a probar (9-A).
     */
    @Transactional
    public void marcarVerificacion(CodigoFuente codigo, String motivo) {
        aplicar(fuente(codigo), false, ResultadoSesion.verificacion(
                motivo != null && !motivo.isBlank() ? motivo : "La tienda pidió un código o CAPTCHA."));
    }

    /** Comprueba cuenta y reglas mínimas antes de cuota, pausa o renovación de sesión. */
    public boolean configuracionCompleta(CodigoFuente codigo) {
        return credenciales.tiene(codigo, TipoCredencial.USUARIO)
                && credenciales.tiene(codigo, TipoCredencial.CLAVE)
                && ReglasMinimas.faltantes(
                        selectorRepository.findByFuente_CodigoAndStatus(codigo, "A")).isEmpty();
    }

    /**
     * Pide al lector iniciar sesión y deja la fuente en el estado que corresponde. true si quedó ACTIVA.
     * manual: «Probar sesión» del Jefe (9-A). false: renovación antes de buscar (D-41).
     */
    private boolean iniciar(FuenteBusquedaConfig fuente, boolean manual) {
        CodigoFuente codigo = fuente.getCodigo();
        String usuarioTienda = credenciales.leer(codigo, TipoCredencial.USUARIO).orElse(null);
        String claveTienda = credenciales.leer(codigo, TipoCredencial.CLAVE).orElse(null);
        ResultadoSesion r = (usuarioTienda == null || claveTienda == null)
                ? ResultadoSesion.sinConfigurar("Falta la cuenta de búsqueda de " + fuente.getNombre())
                : lector.iniciarSesion(codigo, fuente.getUrlBase(), usuarioTienda, claveTienda);
        if (r == null) {
            r = ResultadoSesion.error("El servicio de lectura no respondió.");
        }
        if (r.resultado() == ResultadoSesion.Resultado.OCUPADO) {
            if (manual) throw new DemasiadasSolicitudesException(r.mensaje());
            return false;
        }
        return aplicar(fuente, manual, r);
    }

    /** Deja la fuente en el estado que corresponde al resultado. true si quedó ACTIVA. */
    private boolean aplicar(FuenteBusquedaConfig fuente, boolean manual, ResultadoSesion r) {
        CodigoFuente codigo = fuente.getCodigo();
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
            fuente.setEstado(estadoSiNoOk(r, manual));
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

    /**
     * Un fallo de conexión, un tiempo agotado o un bloqueo quedan en CON_ERROR.
     * En «Probar sesión», faltar la cuenta o las reglas de ingreso no es un fallo de la tienda:
     * SIN_PROBAR. También en la renovación antes de buscar (D-41): falta de configuración
     * reemplaza la decisión anterior de CON_ERROR y no genera aviso al Jefe.
     */
    private static EstadoFuente estadoSiNoOk(ResultadoSesion r, boolean manual) {
        return switch (r.resultado()) {
            case VERIFICACION -> EstadoFuente.REQUIERE_VERIFICACION;
            case SIN_CONFIGURAR -> EstadoFuente.SIN_PROBAR;
            default -> EstadoFuente.CON_ERROR;
        };
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
