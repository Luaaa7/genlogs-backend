package com.genlogs.lector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.genlogs.app.sourcing.lector.ContratoLector.*;
import com.genlogs.app.sourcing.lector.LectorRemotoException;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** P1: un Playwright y un contexto efímero por llamada, siempre en el mismo hilo. */
public final class LectorChromium implements ServicioLectura.Motor {
    private static final String CAPTCHA = "iframe[src*=recaptcha], iframe[src*=hcaptcha], #captchacharacters, #captcha, input[autocomplete=one-time-code]";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final long timeoutMs;
    private final Consumer<BrowserContext> fixture;

    public LectorChromium(long timeoutMs) { this(timeoutMs, context -> { }); }
    /** Solo las pruebas inyectan HTML: no hay variable ni endpoint que habilite URLs locales. */
    LectorChromium(long timeoutMs, Consumer<BrowserContext> fixture) {
        this.timeoutMs = timeoutMs; this.fixture = fixture;
    }

    @Override public Respuesta ejecutar(String operacion, Peticion req) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000;
        try {
            ValidacionLectura.pedido(operacion, req);
            try (ProxySalidaSegura proxy = new ProxySalidaSegura(); Playwright playwright = crearPlaywright();
                 Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true)
                         .setTimeout(restante(deadline)).setProxy(new Proxy(proxy.url()))
                         .setArgs(List.of("--proxy-bypass-list=<-loopback>", "--disable-quic",
                                 "--force-webrtc-ip-handling-policy=disable_non_proxied_udp")))) {
                Browser.NewContextOptions options = new Browser.NewContextOptions()
                        .setServiceWorkers(ServiceWorkerPolicy.BLOCK).setAcceptDownloads(false);
                if (req.sesion() != null) options.setStorageState(req.sesion().storageState().toString());
                try (BrowserContext context = browser.newContext(options)) {
                    fixture.accept(context);
                    context.routeWebSocket(Pattern.compile(".*"), socket -> socket.close());
                    context.route("**/*", route -> {
                        try {
                            URI uri = URI.create(route.request().url());
                            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                                    || (uri.getPort() != -1 && uri.getPort() != 443)) route.abort();
                            else route.fallback(); // Every DNS connection, including redirects, goes through the proxy.
                        } catch (RuntimeException e) { route.abort(); }
                    });
                    context.setDefaultTimeout(restante(deadline));
                    Page page = context.newPage();
                    Response response = page.navigate(req.url(), new Page.NavigateOptions()
                            .setTimeout(restante(deadline)).setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                    ValidacionLectura.url(page.url(), req.fuente());
                    Estado estado = detectar(page, req.reglas(), response == null ? 200 : response.status());
                    if (estado != null) return estado(estado);
                    if ("sesion".equals(operacion)) return iniciarSesion(page, context, req, deadline);
                    String listo = texto(req.reglas(), "listo");
                    if (listo == null) listo = "buscar".equals(operacion) ? texto(req.reglas(), "contenedor")
                            : texto(campo(req.reglas(), "nombre"), "selector");
                    esperar(page, req.reglas(), listo, deadline);
                    estado = detectar(page, req.reglas(), 200);
                    if (estado != null) return estado(estado);
                    ValidacionLectura.url(page.url(), req.fuente());
                    restante(deadline);
                    String html = page.content();
                    if (html.length() > 3 * 1024 * 1024) return estado(Estado.REGLAS_INVALIDAS);
                    List<Resultado> resultados = extraer(html, page.url(), req, "buscar".equals(operacion));
                    restante(deadline);
                    return new Respuesta(Estado.OK, resultados, null);
                }
            }
        } catch (LectorRemotoException e) { return estado(e.getEstado()); }
        catch (TimeoutError e) { return estado(Estado.TIEMPO_AGOTADO); }
        catch (Exception e) {
            // Playwright errors may contain secrets: do not attach/log the exception or its message.
            boolean selector = e.getMessage() != null && (e.getMessage().contains("selector") || e.getMessage().contains("querySelector"));
            return estado(selector ? Estado.REGLAS_INVALIDAS : Estado.SIN_RESPUESTA);
        }
    }

    /** Chromium se instala antes de arrancar; una lectura nunca debe descargar navegadores. */
    static Playwright crearPlaywright() {
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        return Playwright.create(new Playwright.CreateOptions().setEnv(environment));
    }

    private Respuesta iniciarSesion(Page page, BrowserContext context, Peticion req, long deadline) throws Exception {
        Map<String, Object> login = campo(req.reglas(), "login");
        String action = (String) page.locator("css=" + texto(login, "clave")).evaluate("el => el.form?.action || location.href");
        URI original = URI.create(req.url()), form = URI.create(action), actual = URI.create(page.url());
        if (!original.getAuthority().equals(form.getAuthority()) || !original.getAuthority().equals(actual.getAuthority())
                || !"https".equals(form.getScheme())) return estado(Estado.URL_NO_PERMITIDA);
        context.setDefaultTimeout(restante(deadline));
        page.locator("css=" + texto(login, "usuario")).fill(req.credenciales().usuario());
        context.setDefaultTimeout(restante(deadline));
        page.locator("css=" + texto(login, "clave")).fill(req.credenciales().clave());
        context.setDefaultTimeout(restante(deadline));
        page.locator("css=" + texto(login, "enviar")).click();
        esperar(page, req.reglas(), texto(login, "exito"), deadline);
        ValidacionLectura.url(page.url(), req.fuente());
        Estado estado = detectar(page, req.reglas(), 200);
        if (estado != null) return estado(estado);
        JsonNode storage = JSON.readTree(context.storageState());
        ArrayNode cookies = (ArrayNode) storage.path("cookies"), origins = (ArrayNode) storage.path("origins");
        for (int i = cookies.size() - 1; i >= 0; i--) {
            if (!ValidacionLectura.dominio(cookies.get(i).path("domain").asText().replaceFirst("^\\.", ""), req.fuente())) cookies.remove(i);
        }
        for (int i = origins.size() - 1; i >= 0; i--) {
            try { ValidacionLectura.url(origins.get(i).path("origin").asText(), req.fuente()); }
            catch (RuntimeException e) { origins.remove(i); }
        }
        Instant expires = Instant.now().plusSeconds(3600);
        for (JsonNode cookie : cookies) {
            long seconds = cookie.path("expires").asLong(-1);
            if (seconds > Instant.now().getEpochSecond()) {
                Instant value = Instant.ofEpochSecond(seconds);
                if (value.isBefore(expires)) expires = value;
            }
        }
        restante(deadline);
        return new Respuesta(Estado.OK, List.of(), new Sesion(expires.toString(), storage));
    }

    private static void esperar(Page page, Map<String, Object> rules, String ready, long deadline) {
        List<String> selectors = new ArrayList<>();
        if (ready != null) selectors.add(ready);
        for (String name : List.of("verificacion", "bloqueo", "sesionVencida", "sinResultados")) {
            if (texto(rules, name) != null) selectors.add(texto(rules, name));
        }
        selectors.add(CAPTCHA);
        page.waitForFunction("selectors => selectors.some(s => { const el = document.querySelector(s); return el && el.getClientRects().length > 0; })",
                selectors, new Page.WaitForFunctionOptions().setTimeout(restante(deadline)));
    }
    private static Estado detectar(Page page, Map<String, Object> rules, int status) {
        if (visible(page, texto(rules, "verificacion")) || visible(page, CAPTCHA)
                || Pattern.compile("captcha|robot check|verify you are human", Pattern.CASE_INSENSITIVE).matcher(page.title()).find()) return Estado.VERIFICACION;
        if (status == 403 || status == 429 || visible(page, texto(rules, "bloqueo"))) return Estado.BLOQUEO;
        if (status == 401 || visible(page, texto(rules, "sesionVencida"))) return Estado.SESION_VENCIDA;
        return status >= 400 ? Estado.SIN_RESPUESTA : null;
    }
    private static boolean visible(Page page, String selector) { return selector != null && page.locator("css=" + selector).first().isVisible(); }

    static List<Resultado> extraer(String html, String url, Peticion req, boolean buscar) {
        Element document = Jsoup.parse(html, url);
        Map<String, Object> rules = req.reglas();
        List<? extends Element> roots = buscar ? document.select(texto(rules, "contenedor")) : List.of(document);
        List<Resultado> results = new ArrayList<>();
        int limit = req.maxResultados() == null ? 8 : req.maxResultados();
        for (Element root : roots.stream().limit(limit).toList()) {
            String nombre = valor(root, campo(rules, "nombre"));
            BigDecimal precio = precio(valor(root, campo(rules, "precio")), texto(rules, "separadorDecimal"));
            String moneda = valor(root, campo(rules, "moneda"));
            if (moneda == null) moneda = texto(rules, "codigoMoneda");
            String enlace = valor(root, campo(rules, "enlace"));
            if (enlace == null && !buscar) enlace = url;
            if (nombre == null || precio == null || moneda == null || !moneda.matches("[A-Z]{3}") || enlace == null) continue;
            try { enlace = ValidacionLectura.url(URI.create(url).resolve(enlace).toString(), req.fuente()).toString(); }
            catch (RuntimeException e) { continue; }
            String imagen = valor(root, campo(rules, "imagen"));
            try {
                URI image = imagen == null ? null : URI.create(url).resolve(imagen);
                imagen = image != null && "https".equals(image.getScheme()) && image.getHost() != null
                        && image.getUserInfo() == null && (image.getPort() == -1 || image.getPort() == 443) ? image.toString() : null;
            } catch (RuntimeException e) { imagen = null; }
            results.add(new Resultado(nombre.substring(0, Math.min(500, nombre.length())), valor(root, campo(rules, "descripcion")),
                    precio, moneda, imagen, req.fuente(), enlace));
        }
        String empty = texto(rules, "sinResultados");
        if (results.isEmpty() && (!roots.isEmpty() || empty == null || document.select(empty).isEmpty())) ValidacionLectura.fallar(Estado.REGLAS_INVALIDAS);
        return results;
    }
    private static BigDecimal precio(String value, String decimal) {
        if (value == null) return null;
        String clean = value.replaceAll("\\s", "").replaceAll("(?i)(USD|PEN|US\\$|S/|\\$|€|£)", "");
        boolean coma = ",".equals(decimal);
        String regex = coma ? "(?:\\d+|\\d{1,3}(?:\\.\\d{3})+)(?:,\\d{1,2})?" : "(?:\\d+|\\d{1,3}(?:,\\d{3})+)(?:\\.\\d{1,2})?";
        if (!clean.matches(regex) || clean.length() > 24) return null;
        BigDecimal result = new BigDecimal(coma ? clean.replace(".", "").replace(',', '.') : clean.replace(",", ""));
        return result.signum() > 0 ? result : null;
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> campo(Map<String, Object> rules, String name) {
        Object value = rules.get(name); return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }
    private static String texto(Map<String, Object> rules, String name) { return rules.get(name) instanceof String s ? s : null; }
    private static String valor(Element root, Map<String, Object> rule) {
        String selector = texto(rule, "selector");
        Element el = selector == null ? null : root.selectFirst(selector);
        if (el == null) return null;
        String attr = texto(rule, "atributo");
        String value = (attr == null ? el.text() : el.attr(attr)).trim();
        return value.isEmpty() ? null : value.substring(0, Math.min(8000, value.length()));
    }
    private static double restante(long deadline) {
        double millis = (deadline - System.nanoTime()) / 1_000_000d;
        if (millis <= 0 || Thread.currentThread().isInterrupted()) ValidacionLectura.fallar(Estado.TIEMPO_AGOTADO);
        return millis;
    }
    static Respuesta estado(Estado estado) { return new Respuesta(estado, List.of(), null); }
}
