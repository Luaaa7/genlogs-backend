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
                                 "--force-webrtc-ip-handling-policy=disable_non_proxied_udp", "--disable-dev-shm-usage")))) {
                System.out.println("Lector " + operacion + " " + req.fuente() + ": navegador listo a los " + transcurridoMs(deadline) + " ms");
                Browser.NewContextOptions options = new Browser.NewContextOptions()
                        .setServiceWorkers(ServiceWorkerPolicy.BLOCK).setAcceptDownloads(false);
                if (req.sesion() != null) options.setStorageState(req.sesion().storageState().toString());
                try (BrowserContext context = browser.newContext(options)) {
                    fixture.accept(context);
                    context.routeWebSocket(Pattern.compile(".*"), socket -> socket.close());
                    context.route("**/*", route -> {
                        try {
                            // Imágenes, medios y tipografías no se necesitan: el lector lee el HTML, y así la página carga más rápido.
                            String tipo = route.request().resourceType();
                            if ("image".equals(tipo) || "media".equals(tipo) || "font".equals(tipo)) { route.abort(); return; }
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
                    System.out.println("Lector " + operacion + " " + req.fuente() + ": página abierta a los " + transcurridoMs(deadline) + " ms");
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
        } catch (LectorRemotoException e) {
            if (e.getEstado() == Estado.TIEMPO_AGOTADO) System.out.println("Lector " + operacion + " " + req.fuente() + ": tiempo agotado a los " + transcurridoMs(deadline) + " ms");
            return estado(e.getEstado());
        }
        catch (TimeoutError e) {
            System.out.println("Lector " + operacion + " " + req.fuente() + ": tiempo agotado a los " + transcurridoMs(deadline) + " ms");
            return estado(Estado.TIEMPO_AGOTADO);
        }
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

    /**
     * Ingreso de la tienda. Si se agota el tiempo, deja en el registro del servicio el paso donde se quedó y
     * qué página veía el navegador (título, dirección sin parámetros y cuántas casillas visibles). Nunca
     * registra selectores con datos, usuarios ni claves.
     */
    private Respuesta iniciarSesion(Page page, BrowserContext context, Peticion req, long deadline) throws Exception {
        String[] paso = {"abrir la página"};
        long hastaAbrir = transcurridoMs(deadline);
        try {
            return iniciarSesionPaso(page, context, req, deadline, paso);
        } catch (TimeoutError e) {
            registrarTiempoAgotado(page, req, paso[0], hastaAbrir);
            throw e;
        } catch (LectorRemotoException e) {
            if (e.getEstado() == Estado.TIEMPO_AGOTADO) registrarTiempoAgotado(page, req, paso[0], hastaAbrir);
            throw e;
        }
    }

    private long transcurridoMs(long deadline) {
        return timeoutMs - Math.max(0, (deadline - System.nanoTime()) / 1_000_000);
    }

    /** https, sin puerto raro y dentro de los dominios de la tienda: el ingreso puede cambiar de subdominio (signin.ebay.com). */
    private static boolean mismaTienda(URI uri, String fuente) {
        return "https".equalsIgnoreCase(uri.getScheme()) && (uri.getPort() == -1 || uri.getPort() == 443)
                && uri.getHost() != null && ValidacionLectura.dominio(uri.getHost().toLowerCase(java.util.Locale.ROOT), fuente);
    }

    private void registrarTiempoAgotado(Page page, Peticion req, String paso, long hastaAbrir) {
        String titulo = "?", sitio = "?", botones = "?";
        long visibles = -1;
        try {
            titulo = page.title();
            if (titulo.length() > 80) titulo = titulo.substring(0, 80);
            URI url = URI.create(page.url());
            sitio = url.getHost() + url.getPath();
            visibles = ((Number) page.evaluate("() => [...document.querySelectorAll('input')].filter(e => e.getClientRects().length > 0).length")).longValue();
            botones = String.valueOf(page.evaluate("() => [...document.querySelectorAll('button, [role=button], input[type=submit]')].filter(e => e.getClientRects().length > 0).slice(0, 8).map(e => (e.innerText || e.value || '').trim().slice(0, 25) + '<' + e.tagName.toLowerCase() + '.' + String(e.className || '').split(' ').filter(Boolean).slice(0, 2).join('.') + '>').join(' ; ')"));
        } catch (RuntimeException ignorado) {
            // La página ya no responde: se registra lo que se alcanzó a saber.
        }
        System.out.println("Ingreso " + req.fuente() + ": se agotó el tiempo en el paso «" + paso + "»"
                + " | abrir la página tardó " + hastaAbrir + " ms de " + timeoutMs + " ms"
                + " | página: «" + titulo + "» " + sitio + " | casillas visibles: " + visibles
                + " | botones visibles: " + botones);
    }

    private Respuesta iniciarSesionPaso(Page page, BrowserContext context, Peticion req, long deadline, String[] paso) throws Exception {
        Map<String, Object> login = campo(req.reglas(), "login");
        String continuar = texto(login, "continuar");
        if (continuar != null) {
            // D-42: primera pantalla solo con el usuario. La clave aparece después de CONTINUAR.
            URI pagina = URI.create(page.url());
            if (!"https".equalsIgnoreCase(pagina.getScheme()) || !mismaTienda(pagina, req.fuente())) {
                return estado(Estado.URL_NO_PERMITIDA);
            }
            paso[0] = "escribir el usuario (USUARIO)";
            context.setDefaultTimeout(restante(deadline));
            page.locator("css=" + texto(login, "usuario")).fill(req.credenciales().usuario());
            paso[0] = "pulsar CONTINUAR";
            context.setDefaultTimeout(restante(deadline));
            page.locator("css=" + continuar).click();
            paso[0] = "esperar que aparezca la clave (CLAVE)";
            context.setDefaultTimeout(restante(deadline));
            page.locator("css=" + texto(login, "clave")).waitFor();
        }
        paso[0] = "ubicar la casilla de la clave (CLAVE)";
        String action = (String) page.locator("css=" + texto(login, "clave")).evaluate("el => el.form?.action || location.href");
        URI form = URI.create(action), actual = URI.create(page.url());
        if (!mismaTienda(form, req.fuente()) || !mismaTienda(actual, req.fuente())
                || !"https".equals(form.getScheme())) return estado(Estado.URL_NO_PERMITIDA);
        if (continuar == null) {
            // Ingreso de una sola pantalla. Con CONTINUAR el usuario ya se escribió en la primera pantalla y en la
            // segunda esa casilla suele desaparecer (eBay, Amazon): volver a llenarla agotaría el tiempo.
            paso[0] = "escribir el usuario (USUARIO)";
            context.setDefaultTimeout(restante(deadline));
            page.locator("css=" + texto(login, "usuario")).fill(req.credenciales().usuario());
        }
        paso[0] = "escribir la clave (CLAVE)";
        context.setDefaultTimeout(restante(deadline));
        page.locator("css=" + texto(login, "clave")).fill(req.credenciales().clave());
        paso[0] = "pulsar ENVIAR";
        context.setDefaultTimeout(restante(deadline));
        page.locator("css=" + texto(login, "enviar")).click();
        paso[0] = "esperar la señal de sesión iniciada (EXITO)";
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

    public static List<Resultado> extraer(String html, String url, Peticion req, boolean buscar) {
        Element document = Jsoup.parse(html, url);
        Map<String, Object> rules = req.reglas();
        List<? extends Element> roots = buscar ? document.select(texto(rules, "contenedor")) : List.of(document);
        List<Resultado> results = new ArrayList<>();
        int limit = req.maxResultados() == null ? 8 : req.maxResultados();
        for (Element root : roots.stream().limit(limit).toList()) {
            String nombre = valor(root, campo(rules, "nombre"));
            String textoPrecio = valor(root, campo(rules, "precio"));
            if (textoPrecio != null && (textoPrecio.indexOf('€') >= 0 || textoPrecio.indexOf('£') >= 0)) continue;
            BigDecimal precio = precio(textoPrecio, texto(rules, "separadorDecimal"),
                    "mayor".equals(texto(rules, "precioRango")));
            String moneda = moneda(valor(root, campo(rules, "moneda")), textoPrecio, texto(rules, "codigoMoneda"));
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
    /**
     * Tres letras de la regla MONEDA ganan. S/ de esa regla queda en PEN.
     * PEN y S/ del precio ganan a US$. Solo USD, US$ y «US $» son dólares.
     * Un $ suelto y un precio sin símbolo usan el respaldo codigoMoneda.
     */
    private static String moneda(String regla, String textoPrecio, String respaldo) {
        if (regla != null) {
            String limpia = regla.trim();
            if (limpia.matches("[A-Z]{3}")) return limpia;
            if ("S/".equals(limpia)) return "PEN";
        }
        if (textoPrecio != null && (textoPrecio.contains("PEN") || textoPrecio.contains("S/"))) return "PEN";
        if (textoPrecio != null && (textoPrecio.contains("USD") || textoPrecio.contains("US$") || textoPrecio.contains("US $"))) {
            return "USD";
        }
        return respaldo;
    }
    /**
     * KA (v3.3): con la regla precioRango="mayor" (solo la manda el lector de Alibaba), un rango
     * "US$12.50-15.00" vale el mayor de los dos: el menor suele ser por cientos de unidades y se
     * compran pocas. Sin esa regla un rango sigue sin ser un precio.
     */
    private static BigDecimal precio(String value, String decimal, boolean rangoMayor) {
        if (value == null) return null;
        if (rangoMayor) {
            String[] partes = value.split("\\s*[-–~]\\s*");
            if (partes.length == 2) {
                BigDecimal a = precio(partes[0], decimal);
                BigDecimal b = precio(partes[1], decimal);
                if (a != null && b != null) return a.max(b);
            }
        }
        return precio(value, decimal);
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
