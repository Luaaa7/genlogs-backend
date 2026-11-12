package com.genlogs.app.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.ParametroResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.ParametroSistema;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.ParametroSistemaRepository;
import com.genlogs.app.repository.UsuarioRepository;

/**
 * Edición de parametro_sistema (RF-87). La lectura sigue en {@link ParametrosService}.
 * Solo cambia el valor de una clave que ya existe.
 */
@Service
public class EdicionParametrosService {

    private static final Set<String> MARGENES_REPUESTO = Set.of(
            ClavesParametro.MARGEN_REP_MIN,
            ClavesParametro.MARGEN_REP_DEFAULT,
            ClavesParametro.MARGEN_REP_MAX);

    private final ParametroSistemaRepository parametroSistemaRepository;
    private final UsuarioRepository usuarioRepository;

    public EdicionParametrosService(ParametroSistemaRepository parametroSistemaRepository,
            UsuarioRepository usuarioRepository) {
        this.parametroSistemaRepository = parametroSistemaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<ParametroResponse> listar() {
        return parametroSistemaRepository.findAll(Sort.by("clave")).stream()
                .map(EdicionParametrosService::aRespuesta)
                .toList();
    }

    @Transactional
    public ParametroResponse actualizar(String clave, String valor) {
        String nuevo = exigirValor(valor);
        ParametroSistema parametro = exigirExistente(clave);
        if (MARGENES_REPUESTO.contains(clave)) {
            exigirMargen(clave, nuevo);
        }
        parametro.setValor(nuevo);
        parametro.setEditadoPor(usuarioActual().getIdUsuario());
        parametro.setEditadoEn(LocalDateTime.now());
        return aRespuesta(parametroSistemaRepository.save(parametro));
    }

    private static String exigirValor(String valor) {
        String nuevo = valor == null ? "" : valor.trim();
        if (nuevo.isEmpty()) {
            throw new BusinessException("El valor del parámetro no puede estar vacío");
        }
        if (nuevo.length() > 100) {
            throw new BusinessException("El valor del parámetro no puede pasar de 100 caracteres");
        }
        return nuevo;
    }

    private ParametroSistema exigirExistente(String clave) {
        return parametroSistemaRepository.findById(clave).orElseThrow(() -> new BusinessException(
                "Falta el parámetro " + clave + " en parametro_sistema. Revisa que se hayan corrido las migraciones v3.2."));
    }

    /** MIN <= DEFAULT <= MAX, usando el valor nuevo para la clave que se está editando. */
    private void exigirMargen(String clave, String nuevo) {
        BigDecimal min = decimal(ClavesParametro.MARGEN_REP_MIN, clave, nuevo);
        BigDecimal porDefecto = decimal(ClavesParametro.MARGEN_REP_DEFAULT, clave, nuevo);
        BigDecimal max = decimal(ClavesParametro.MARGEN_REP_MAX, clave, nuevo);
        if (min.compareTo(porDefecto) > 0 || porDefecto.compareTo(max) > 0) {
            throw new BusinessException(
                    "El margen mínimo de repuestos no puede superar al de por defecto, ni este al máximo.");
        }
    }

    private BigDecimal decimal(String claveLeida, String claveEditada, String nuevo) {
        String texto = claveLeida.equals(claveEditada) ? nuevo : exigirExistente(claveLeida).getValor();
        try {
            return new BigDecimal(texto.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("El parámetro " + claveLeida + " debe ser un número y vale \"" + texto + "\".");
        }
    }

    private Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
                || auth.getName() == null || auth.getName().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("No hay un usuario autenticado en la petición");
        }
        return usuarioRepository.findByNombreUsuario(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));
    }

    private static ParametroResponse aRespuesta(ParametroSistema parametro) {
        return new ParametroResponse(parametro.getClave(), parametro.getValor(), parametro.getDescripcion(),
                parametro.getEditadoEn());
    }
}
