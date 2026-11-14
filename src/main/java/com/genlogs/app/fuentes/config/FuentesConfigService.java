package com.genlogs.app.fuentes.config;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.EstadoNoPermitidoException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * L5 (v3.3) · Configuración de las tiendas de la búsqueda por scraping (RF-88, RF-89).
 * Las credenciales solo se escriben (cifradas por CredencialesFuenteService); al cambiarlas
 * la sesión guardada deja de valer y la fuente vuelve a SIN_PROBAR. Una fuente solo se
 * activa con la sesión probada y 4 o más aciertos en la prueba de 10 pedidos (D-37).
 */
@Service
@RequiredArgsConstructor
public class FuentesConfigService {

    static final int ACIERTOS_MINIMOS = 4;
    private static final String PROCESO = "CONFIG_FUENTE";

    private final FuenteBusquedaConfigRepository fuenteRepository;
    private final FuenteCredencialRepository credencialRepository;
    private final FuenteSelectorRepository selectorRepository;
    private final CredencialesFuenteService credenciales;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<FuenteConfigResponse> listar() {
        return fuenteRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getCodigo().ordinal(), b.getCodigo().ordinal()))
                .map(this::aRespuesta)
                .toList();
    }

    @Transactional
    public void guardarCredenciales(String codigoTexto, CredencialesFuenteRequest req) {
        CodigoFuente codigo = codigo(codigoTexto);
        FuenteBusquedaConfig fuente = fuente(codigo);
        boolean hayUsuario = req != null && req.usuario() != null && !req.usuario().isBlank();
        boolean hayClave = req != null && req.clave() != null && !req.clave().isBlank();
        if (!hayUsuario && !hayClave) {
            throw new BusinessException("Indica el usuario o la clave de la tienda");
        }
        String nombre = usuarioActual();
        Long idUsuario = idUsuario(nombre);
        if (hayUsuario) {
            credenciales.guardar(codigo, TipoCredencial.USUARIO, req.usuario().trim(), idUsuario, nombre);
        }
        if (hayClave) {
            credenciales.guardar(codigo, TipoCredencial.CLAVE, req.clave(), idUsuario, nombre);
        }
        // Con otra cuenta, la sesión guardada ya no sirve: hay que volver a probarla (L1).
        credencialRepository.findByFuente_CodigoAndTipo(codigo, TipoCredencial.SESION)
                .ifPresent(credencialRepository::delete);
        fuente.setEstado(EstadoFuente.SIN_PROBAR);
        fuente.setSesionValidaHasta(null);
        auditar(fuente, nombre, idUsuario);
    }

    @Transactional
    public FuenteConfigResponse activar(String codigoTexto, boolean activa) {
        FuenteBusquedaConfig fuente = fuente(codigo(codigoTexto));
        if (activa) {
            if (fuente.getEstado() != EstadoFuente.ACTIVA) {
                throw new EstadoNoPermitidoException(
                        "Primero prueba la sesión de " + fuente.getNombre() + ": hoy está " + fuente.getEstado());
            }
            Short aciertos = fuente.getAciertosPrueba();
            if (aciertos == null || aciertos < ACIERTOS_MINIMOS) {
                throw new EstadoNoPermitidoException(fuente.getNombre()
                        + " necesita 4 o más aciertos en la prueba de 10 pedidos para activarse (D-37)");
            }
        }
        fuente.setActiva(activa);
        String nombre = usuarioActual();
        auditar(fuente, nombre, idUsuario(nombre));
        return aRespuesta(fuente);
    }

    @Transactional
    public FuenteConfigResponse cambiarParametros(String codigoTexto, ParametrosFuenteRequest req) {
        FuenteBusquedaConfig fuente = fuente(codigo(codigoTexto));
        fuente.setPausaSegundos(req.pausaSegundos());
        fuente.setLimiteDiario(req.limiteDiario());
        String nombre = usuarioActual();
        auditar(fuente, nombre, idUsuario(nombre));
        return aRespuesta(fuente);
    }

    @Transactional(readOnly = true)
    public List<SelectorFuenteDto> listarSelectores(String codigoTexto) {
        CodigoFuente codigo = codigo(codigoTexto);
        fuente(codigo);
        return selectorRepository.findByFuente_CodigoAndStatus(codigo, "A").stream()
                .map(s -> new SelectorFuenteDto(s.getPagina(), s.getCampo(), s.getRegla()))
                .toList();
    }

    /** Crea o reemplaza las reglas enviadas; las que no vienen se quedan como están. */
    @Transactional
    public void guardarSelectores(String codigoTexto, List<SelectorFuenteDto> reglas) {
        CodigoFuente codigo = codigo(codigoTexto);
        FuenteBusquedaConfig fuente = fuente(codigo);
        if (reglas == null || reglas.isEmpty()) {
            throw new BusinessException("Envía al menos una regla");
        }
        String nombre = usuarioActual();
        Long idUsuario = idUsuario(nombre);
        List<FuenteSelector> actuales = selectorRepository.findByFuente_CodigoAndStatus(codigo, "A");
        for (SelectorFuenteDto r : reglas) {
            FuenteSelector selector = actuales.stream()
                    .filter(s -> s.getPagina() == r.pagina() && s.getCampo() == r.campo())
                    .findFirst()
                    .orElseGet(() -> {
                        FuenteSelector nuevo = new FuenteSelector();
                        nuevo.setFuente(fuente);
                        nuevo.setPagina(r.pagina());
                        nuevo.setCampo(r.campo());
                        nuevo.setUserCreate(nombre);
                        nuevo.setProcessCreate(PROCESO);
                        return nuevo;
                    });
            selector.setRegla(r.regla().trim());
            selector.setEditadoPor(idUsuario);
            if (selector.getIdSelector() != null) {
                selector.setUserUpdate(nombre);
                selector.setProcessUpdate(PROCESO);
                selector.setDateUpdate(LocalDateTime.now());
            }
            selectorRepository.save(selector);
        }
    }

    // ---------------------------------------------------------------- apoyo

    private FuenteConfigResponse aRespuesta(FuenteBusquedaConfig f) {
        return new FuenteConfigResponse(
                f.getCodigo().name(), f.getNombre(), f.getUrlBase(),
                Boolean.TRUE.equals(f.getActiva()), f.getEstado().name(),
                f.getSesionValidaHasta(), f.getUltimaPrueba(), f.getUltimoError(),
                f.getPausaSegundos(), f.getLimiteDiario(), f.getAciertosPrueba(),
                credenciales.tiene(f.getCodigo(), TipoCredencial.USUARIO),
                credenciales.tiene(f.getCodigo(), TipoCredencial.CLAVE));
    }

    private static CodigoFuente codigo(String texto) {
        return Arrays.stream(CodigoFuente.values())
                .filter(c -> c.name().equalsIgnoreCase(texto))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No existe la fuente " + texto));
    }

    private FuenteBusquedaConfig fuente(CodigoFuente codigo) {
        return fuenteRepository.findByCodigo(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la fuente " + codigo));
    }

    private void auditar(FuenteBusquedaConfig fuente, String nombre, Long idUsuario) {
        fuente.setEditadoPor(idUsuario);
        fuente.setUserUpdate(nombre);
        fuente.setProcessUpdate(PROCESO);
        fuente.setDateUpdate(LocalDateTime.now());
        fuenteRepository.save(fuente);
    }

    private static String usuarioActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SISTEMA";
    }

    private Long idUsuario(String nombre) {
        return usuarioRepository.findByNombreUsuario(nombre).map(u -> u.getIdUsuario()).orElse(null);
    }
}
