package com.genlogs.app.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
 *
 * <ul>
 *   <li>Las claves conocidas de {@link ClavesParametro} son numéricas y no aceptan negativos;
 *       las de días y cantidades, además, deben ser enteras.</li>
 *   <li>Los márgenes de repuestos (D-17) cumplen MIN &lt;= DEFAULT &lt;= MAX. Se pueden editar
 *       de a uno o los tres juntos con {@link #actualizarMargenesRepuesto}.</li>
 *   <li>Antes de validar se bloquean las filas que se van a leer y escribir, para que dos
 *       ediciones simultáneas no dejen los márgenes inconsistentes.</li>
 * </ul>
 */
@Service
public class EdicionParametrosService {

    private static final Set<String> MARGENES_REPUESTO = Set.of(
            ClavesParametro.MARGEN_REP_MIN,
            ClavesParametro.MARGEN_REP_DEFAULT,
            ClavesParametro.MARGEN_REP_MAX);

    /** Días y cantidades: enteros no negativos (se leen con ParametrosService.getEntero). */
    private static final Set<String> ENTEROS = Set.of(
            ClavesParametro.PLAZO_COMUN_DIAS,
            ClavesParametro.PLAZO_IMPORTADO_DIAS,
            ClavesParametro.PLAZO_ESPECIAL_DIAS,
            ClavesParametro.VERIFICACION_DIAS,
            ClavesParametro.OCULTAR_SIN_VERIF_DIAS,
            ClavesParametro.OCULTAR_FALLAS);

    /** Montos y porcentajes: decimales no negativos (se leen con ParametrosService.getDecimal). */
    private static final Set<String> DECIMALES = Set.of(
            ClavesParametro.MONTO_ALTO_VALOR_PEN,
            ClavesParametro.MARGEN_SERV_SUGERIDO,
            ClavesParametro.MARGEN_SERV_MIN,
            ClavesParametro.MARGEN_SERV_AVISO,
            ClavesParametro.MARGEN_SERV_MAX,
            ClavesParametro.ALERTA_PLAZO_PCT,
            ClavesParametro.MARGEN_REP_MIN,
            ClavesParametro.MARGEN_REP_DEFAULT,
            ClavesParametro.MARGEN_REP_MAX);

    private static final Pattern ENTERO = Pattern.compile("\\d+");
    private static final Pattern DECIMAL = Pattern.compile("\\d+(\\.\\d+)?");

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
        ParametroSistema parametro;
        if (MARGENES_REPUESTO.contains(clave)) {
            Map<String, ParametroSistema> margenes = bloquearMargenes();
            exigirFormato(clave, nuevo);
            exigirOrden(
                    valorMargen(margenes, ClavesParametro.MARGEN_REP_MIN, clave, nuevo),
                    valorMargen(margenes, ClavesParametro.MARGEN_REP_DEFAULT, clave, nuevo),
                    valorMargen(margenes, ClavesParametro.MARGEN_REP_MAX, clave, nuevo));
            parametro = margenes.get(clave);
        } else {
            parametro = bloquearExistente(clave);
            exigirFormato(clave, nuevo);
        }
        guardar(parametro, nuevo, usuarioActual().getIdUsuario(), LocalDateTime.now());
        return aRespuesta(parametro);
    }

    /**
     * Cambia MIN, DEFAULT y MAX de repuestos en una sola transacción: se guardan los tres o
     * ninguno. Sirve para mover todo el rango (p. ej. de 15/35/45 a 50/60/70), que de a uno
     * exigiría editar en un orden exacto.
     */
    @Transactional
    public List<ParametroResponse> actualizarMargenesRepuesto(BigDecimal min, BigDecimal porDefecto, BigDecimal max) {
        exigirNoNegativo(ClavesParametro.MARGEN_REP_MIN, min);
        exigirNoNegativo(ClavesParametro.MARGEN_REP_DEFAULT, porDefecto);
        exigirNoNegativo(ClavesParametro.MARGEN_REP_MAX, max);
        Map<String, ParametroSistema> margenes = bloquearMargenes();
        exigirOrden(min, porDefecto, max);

        Long idUsuario = usuarioActual().getIdUsuario();
        LocalDateTime ahora = LocalDateTime.now();
        guardarSiCambia(margenes.get(ClavesParametro.MARGEN_REP_MIN), min, idUsuario, ahora);
        guardarSiCambia(margenes.get(ClavesParametro.MARGEN_REP_DEFAULT), porDefecto, idUsuario, ahora);
        guardarSiCambia(margenes.get(ClavesParametro.MARGEN_REP_MAX), max, idUsuario, ahora);

        return List.of(
                aRespuesta(margenes.get(ClavesParametro.MARGEN_REP_MIN)),
                aRespuesta(margenes.get(ClavesParametro.MARGEN_REP_DEFAULT)),
                aRespuesta(margenes.get(ClavesParametro.MARGEN_REP_MAX)));
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

    /** Las claves que no están en ENTEROS ni DECIMALES se guardan como texto, igual que antes. */
    private static void exigirFormato(String clave, String nuevo) {
        boolean entero = ENTEROS.contains(clave);
        if (!entero && !DECIMALES.contains(clave)) {
            return;
        }
        if (nuevo.startsWith("-")) {
            throw new BusinessException("El parámetro " + clave + " no puede ser negativo.");
        }
        if (entero) {
            if (!ENTERO.matcher(nuevo).matches()) {
                throw new BusinessException("El parámetro " + clave + " debe ser un número entero sin decimales.");
            }
            try {
                Integer.parseInt(nuevo);
            } catch (NumberFormatException e) {
                throw new BusinessException("El parámetro " + clave + " es demasiado grande.");
            }
        } else if (!DECIMAL.matcher(nuevo).matches()) {
            throw new BusinessException("El parámetro " + clave + " debe ser un número (use punto para los decimales).");
        }
    }

    private static void exigirNoNegativo(String clave, BigDecimal valor) {
        if (valor == null) {
            throw new BusinessException("El parámetro " + clave + " es obligatorio.");
        }
        if (valor.signum() < 0) {
            throw new BusinessException("El parámetro " + clave + " no puede ser negativo.");
        }
    }

    private static void exigirOrden(BigDecimal min, BigDecimal porDefecto, BigDecimal max) {
        if (min.compareTo(porDefecto) > 0 || porDefecto.compareTo(max) > 0) {
            throw new BusinessException(
                    "El margen mínimo de repuestos no puede superar al de por defecto, ni este al máximo.");
        }
    }

    /** Bloquea las tres filas de márgenes de repuestos; si falta alguna, error claro. */
    private Map<String, ParametroSistema> bloquearMargenes() {
        Map<String, ParametroSistema> margenes = parametroSistemaRepository.bloquearPorClaves(MARGENES_REPUESTO)
                .stream()
                .collect(Collectors.toMap(ParametroSistema::getClave, Function.identity()));
        for (String clave : List.of(ClavesParametro.MARGEN_REP_MIN, ClavesParametro.MARGEN_REP_DEFAULT,
                ClavesParametro.MARGEN_REP_MAX)) {
            if (!margenes.containsKey(clave)) {
                throw faltaParametro(clave);
            }
        }
        return margenes;
    }

    private ParametroSistema bloquearExistente(String clave) {
        return parametroSistemaRepository.bloquearPorClaves(Set.of(clave)).stream()
                .findFirst()
                .orElseThrow(() -> faltaParametro(clave));
    }

    private static BusinessException faltaParametro(String clave) {
        return new BusinessException(
                "Falta el parámetro " + clave + " en parametro_sistema. Revisa que se hayan corrido las migraciones v3.2.");
    }

    /** Valor del margen: el nuevo si es la clave que se edita; si no, el guardado (ya bloqueado). */
    private static BigDecimal valorMargen(Map<String, ParametroSistema> margenes, String claveLeida,
            String claveEditada, String nuevo) {
        String texto = claveLeida.equals(claveEditada) ? nuevo : margenes.get(claveLeida).getValor();
        try {
            return new BigDecimal(texto.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("El parámetro " + claveLeida + " debe ser un número y vale \"" + texto + "\".");
        }
    }

    private void guardarSiCambia(ParametroSistema parametro, BigDecimal valor, Long idUsuario, LocalDateTime ahora) {
        String nuevo = valor.stripTrailingZeros().toPlainString();
        if (!nuevo.equals(parametro.getValor() == null ? null : parametro.getValor().trim())) {
            guardar(parametro, nuevo, idUsuario, ahora);
        }
    }

    private void guardar(ParametroSistema parametro, String valor, Long idUsuario, LocalDateTime ahora) {
        parametro.setValor(valor);
        parametro.setEditadoPor(idUsuario);
        parametro.setEditadoEn(ahora);
        parametroSistemaRepository.save(parametro);
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
