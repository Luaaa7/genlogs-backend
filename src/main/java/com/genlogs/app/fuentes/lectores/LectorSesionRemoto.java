package com.genlogs.app.fuentes.lectores;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genlogs.app.fuentes.config.CampoSelector;
import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.FuenteSelector;
import com.genlogs.app.fuentes.config.FuenteSelectorRepository;
import com.genlogs.app.fuentes.config.LectorSesion;
import com.genlogs.app.fuentes.config.PaginaSelector;
import com.genlogs.app.fuentes.config.ResultadoSesion;
import com.genlogs.app.sourcing.lector.ContratoLector;
import com.genlogs.app.sourcing.lector.LectorRemotoClient;
import com.genlogs.app.sourcing.lector.LectorRemotoException;

/**
 * L1 (v3.3, BPMN 9-A) · «Probar sesión» con el servicio de lectura (P2). Abre la página de ingreso
 * de la tienda, llena la cuenta de búsqueda con las reglas LOGIN (USUARIO, CLAVE, ENVIAR y EXITO)
 * y devuelve la sesión que guarda {@link com.genlogs.app.fuentes.config.SesionFuenteService}.
 * No guarda nada ni cambia estados: solo traduce.
 *
 * <p>La sesión se entrega en el formato que leen los lectores al buscar
 * ({@link PuenteLector#sesion}): {"validaHasta": "...", "storageState": {...}}.</p>
 */
@Component
public class LectorSesionRemoto implements LectorSesion {

    /**
     * Página de ingreso de cada tienda (decisión 17/11: fija en el código, como las direcciones de
     * búsqueda de los lectores). Por confirmar con la página real. url_base no sirve: el formulario
     * de ingreso no está en la página principal.
     */
    static final Map<CodigoFuente, String> PAGINA_INGRESO = Map.of(
            CodigoFuente.MERCADO_LIBRE, "https://www.mercadolibre.com/jms/mpe/lgz/login",
            CodigoFuente.EBAY, "https://signin.ebay.com/signin/",
            CodigoFuente.AMAZON, "https://www.amazon.com/ap/signin"
                    + "?openid.pape.max_auth_age=0"
                    + "&openid.return_to=https%3A%2F%2Fwww.amazon.com%2F"
                    + "&openid.identity=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select"
                    + "&openid.assoc_handle=usflex"
                    + "&openid.mode=checkid_setup"
                    + "&openid.claimed_id=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select"
                    + "&openid.ns=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0",
            CodigoFuente.ALIEXPRESS, "https://www.aliexpress.com/p/ug-login-page/login.html",
            CodigoFuente.ALIBABA, "https://login.alibaba.com/");

    static final Map<CodigoFuente, String> TIENDA = Map.of(
            CodigoFuente.MERCADO_LIBRE, "Mercado Libre",
            CodigoFuente.EBAY, "eBay",
            CodigoFuente.AMAZON, "Amazon",
            CodigoFuente.ALIEXPRESS, "AliExpress",
            CodigoFuente.ALIBABA, "Alibaba");

    /** Campo de la página LOGIN → clave de la regla "login" del contrato del servicio de lectura. */
    private static final Map<CampoSelector, String> CAMPOS_LOGIN = Map.of(
            CampoSelector.USUARIO, "usuario",
            CampoSelector.CLAVE, "clave",
            CampoSelector.ENVIAR, "enviar",
            CampoSelector.EXITO, "exito",
            CampoSelector.CONTINUAR, "continuar");
    /** Las cuatro de siempre. CONTINUAR no es obligatoria: el ingreso de una sola pantalla no la usa. */
    private static final Set<String> REGLAS_OBLIGATORIAS = Set.of("usuario", "clave", "enviar", "exito");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final LectorRemotoClient cliente;
    private final FuenteSelectorRepository selectores;
    private final Clock reloj;

    @Autowired
    public LectorSesionRemoto(LectorRemotoClient cliente, FuenteSelectorRepository selectores) {
        this(cliente, selectores, Clock.systemDefaultZone());
    }

    LectorSesionRemoto(LectorRemotoClient cliente, FuenteSelectorRepository selectores, Clock reloj) {
        this.cliente = cliente;
        this.selectores = selectores;
        this.reloj = reloj;
    }

    @Override
    public ResultadoSesion iniciarSesion(CodigoFuente codigo, String urlBase, String usuario, String clave) {
        String tienda = TIENDA.getOrDefault(codigo, String.valueOf(codigo));
        String pagina = PAGINA_INGRESO.get(codigo);
        if (pagina == null) {
            return ResultadoSesion.error(tienda + ": no tiene página de inicio de sesión.");
        }
        Map<String, String> login = reglasLogin(codigo);
        if (codigo == CodigoFuente.AMAZON && "#ap_email".equals(login.get("usuario"))) {
            // Amazon usa ambos identificadores según la variante de su página de ingreso.
            login.put("usuario", ":is(#ap_email, #ap_email_login):visible");
        }
        if (!login.keySet().containsAll(REGLAS_OBLIGATORIAS)) {
            return ResultadoSesion.sinConfigurar(tienda + ": faltan reglas de la página de inicio de sesión (casilla del"
                    + " usuario, casilla de la clave, botón para ingresar y señal de sesión iniciada) en"
                    + " Configuración > Fuentes > Reglas de extracción.");
        }
        ContratoLector.Respuesta respuesta;
        try {
            respuesta = cliente.iniciarSesion(new ContratoLector.Peticion(codigo.name(), pagina,
                    Map.of("login", login), null, new ContratoLector.Credenciales(usuario, clave), null));
        } catch (LectorRemotoException e) {
            String mensaje = tienda + ": " + e.getMessage();
            return e.getEstado() == ContratoLector.Estado.VERIFICACION
                    ? ResultadoSesion.verificacion(mensaje)
                    : ResultadoSesion.error(mensaje);
        } catch (RuntimeException e) {
            // Sin el detalle: podría traer datos de la petición. «Probar sesión» no debe terminar en 500.
            return ResultadoSesion.error(tienda + ": no se pudo iniciar sesión con el servicio de lectura.");
        }
        return aResultado(tienda, respuesta);
    }

    /** Las 4 reglas LOGIN activas de la tienda, con la clave que espera el servicio de lectura. */
    private Map<String, String> reglasLogin(CodigoFuente codigo) {
        Map<String, String> login = new HashMap<>();
        for (FuenteSelector s : selectores.findByFuente_CodigoAndStatus(codigo, "A")) {
            String clave = CAMPOS_LOGIN.get(s.getCampo());
            if (s.getPagina() == PaginaSelector.LOGIN && clave != null
                    && s.getRegla() != null && !s.getRegla().isBlank()) {
                login.put(clave, s.getRegla().trim());
            }
        }
        return login;
    }

    /** La sesión lista para guardar, o un error si el servicio de lectura no la devolvió completa. */
    private ResultadoSesion aResultado(String tienda, ContratoLector.Respuesta respuesta) {
        String sinSesion = tienda + ": el servicio de lectura no devolvió una sesión válida.";
        if (respuesta == null || respuesta.sesion() == null || respuesta.sesion().storageState() == null) {
            return ResultadoSesion.error(sinSesion);
        }
        try {
            Instant hasta = Instant.parse(respuesta.sesion().validaHasta());
            Map<String, Object> sesion = new LinkedHashMap<>();
            sesion.put("validaHasta", hasta.toString());
            sesion.put("storageState", respuesta.sesion().storageState());
            return ResultadoSesion.ok(JSON.writeValueAsString(sesion), LocalDateTime.ofInstant(hasta, reloj.getZone()));
        } catch (Exception e) {
            // Sin el detalle: la sesión trae cookies.
            return ResultadoSesion.error(sinSesion);
        }
    }
}
