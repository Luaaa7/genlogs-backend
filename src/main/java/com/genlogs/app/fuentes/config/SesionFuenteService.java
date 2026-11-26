package com.genlogs.app.fuentes.config;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.fuentes.lectores.SerpApiCliente;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * L1 (v3.3, BPMN 9-A) - Sesion de cada tienda con su cuenta de busqueda (D-38).
 * eBay y Amazon, cuando SERPAPI_KEY esta configurada, no necesitan login en la tienda:
 * la sesion se marca valida y la busqueda va por SerpApi.
 */
@Service
@RequiredArgsConstructor
public class SesionFuenteService {

    static final Duration MARGEN_RENOVACION = Duration.ofMinutes(10);
    private static final String PROCESO = "SESION_FUENTE";
    private static final int LARGO_ERROR = 500;
    static final String SESION_SERPAPI = "{\"cookies\":[],\"origins\":[]}";

    private final FuenteBusquedaConfigRepository fuenteRepository;
    private final FuenteCredencialRepository credencialRepository;
    private final CredencialesFuenteService credenciales;
    private final FuenteSelectorRepository selectorRepository;
    private final FuentesConfigService config;
    private final LectorSesion lector;
    private final UsuarioRepository usuarioRepository;

    private SerpApiCliente serpApi;

    @Autowired(required = false)
    public void setSerpApi(SerpApiCliente serpApi) {
        this.serpApi = serpApi;
    }

    /** eBay y Amazon con SerpApi configurado no usan login ni reglas de la tienda. */
    boolean usaSerpApi(CodigoFuente codigo) {
        return serpApi != null && serpApi.configurado()
                && (codigo == CodigoFuente.EBAY || codigo == CodigoFuente.AMAZON);
    }

    @Transactional
    public FuenteConfigResponse probar(String codigoTexto) {
        CodigoFuente codigo = FuentesConfigService.codigo(codigoTexto);
        FuenteBusquedaConfig fuente = fuente(codigo);
        if (usaSerpApi(codigo)) {
            aplicar(fuente, true, ResultadoSesion.ok(SESION_SERPAPI, LocalDateTime.now().plusDays(365)));
            return config.aRespuesta(fuente);
        }
        if (!credenciales.tiene(codigo, TipoCredencial.USUARIO) || !credenciales.tiene(codigo, TipoCredencial.CLAVE)) {
            throw new BusinessException("Primero guarda la cuenta de busqueda (usuario y clave) de " + fuente.getNombre());
        }
        iniciar(fuente, true);
        return config.aRespuesta(fuente);
    }

    @Transactional
    public Optional<String> sesionVigente(CodigoFuente codigo) {
        FuenteBusquedaConfig fuente = fuente(codigo);
        if (fuente.getEstado() != EstadoFuente.ACTIVA) {
            return Optional.empty();
        }
        if (usaSerpApi(codigo)) {
            return Optional.of(SESION_SERPAPI);
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

    @Transactional
    public void marcarVerificacion(CodigoFuente codigo, String motivo) {
        aplicar(fuente(codigo), false, ResultadoSesion.verificacion(
                motivo != null && !motivo.isBlank() ? motivo : "La tienda pidio un codigo o CAPTCHA."));
    }

    public boolean configuracionCompleta(CodigoFuente codigo) {
        if (usaSerpApi(codigo)) {
            return true;
        }
        return credenciales.tiene(codigo, TipoCredencial.USUARIO)
                && credenciales.tiene(codigo, TipoCredencial.CLAVE)
                && ReglasMinimas.faltantes(
                        selectorRepository.findByFuente_CodigoAndStatus(codigo, "A")).isEmpty();
    }

    private boolean iniciar(FuenteBusquedaConfig fuente, boolean manual) {
        CodigoFuente codigo = fuente.getCodigo();
        String usuarioTienda = credenciales.leer(codigo, TipoCredencial.USUARIO).orElse(null);
        String claveTienda = credenciales.leer(codigo, TipoCredencial.CLAVE).orElse(null);
        ResultadoSesion r = (usuarioTienda == null || claveTienda == null)
                ? ResultadoSesion.sinConfigurar("Falta la cuenta de busqueda de " + fuente.getNombre())
                : lector.iniciarSesion(codigo, fuente.getUrlBase(), usuarioTienda, claveTienda);
        if (r == null) {
            r = ResultadoSesion.error("El servicio de lectura no respondio.");
        }
        return aplicar(fuente, manual, r);
    }

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
            fuente.setUltimoError(recortar(r.mensaje() != null ? r.mensaje() : "No se pudo iniciar sesion en la tienda."));
        }
        fuente.setUltimaPrueba(LocalDateTime.now());
        fuente.setEditadoPor(idUsuario);
        fuente.setUserUpdate(nombre);
        fuente.setProcessUpdate(PROCESO);
        fuente.setDateUpdate(LocalDateTime.now());
        fuenteRepository.save(fuente);
        return ok;
    }

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