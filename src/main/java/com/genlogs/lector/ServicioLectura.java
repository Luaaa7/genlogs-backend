package com.genlogs.lector;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genlogs.app.sourcing.lector.ContratoLector.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P1: proceso Java separado; no es un bean Spring ni abre endpoints en el backend.
 * Arranque: java -cp "target/classes;DEPENDENCIAS" com.genlogs.lector.ServicioLectura.
 * GENLOGS_LECTOR_TOKEN (32+ caracteres), PORT (3001), GENLOGS_LECTOR_TIMEOUT_MS (20000).
 * Las dependencias para el proceso se obtienen con mvnw dependency:build-classpath.
 * Instalar Chromium con com.microsoft.playwright.CLI install chromium en ese classpath.
 * P3 y Render corresponden a Nicole. No se agrega Dockerfile por el alcance Java solicitado.
 */
public final class ServicioLectura implements AutoCloseable {
    @FunctionalInterface public interface Motor { Respuesta ejecutar(String operacion, Peticion peticion); }
    private static final Logger log = LoggerFactory.getLogger(ServicioLectura.class);
    private static final int MAX_BODY = 512 * 1024;
    // Cada operación abre Chromium. Dos procesos pueden agotar el plan de 512 MiB.
    private static final int MAX_LECTURAS_SIMULTANEAS = 1;
        private static final ObjectMapper JSON = new ObjectMapper().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    private final HttpServer server;
    private final ExecutorService handlers = Executors.newFixedThreadPool(4);
    private final ExecutorService workers = Executors.newFixedThreadPool(MAX_LECTURAS_SIMULTANEAS);
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor();
    private final Semaphore slots = new Semaphore(MAX_LECTURAS_SIMULTANEAS);
    private final byte[] tokenDigest;
    private final Motor motor;
    private final long timeoutMs;

    public ServicioLectura(InetSocketAddress bind, String token, long timeoutMs, Motor motor) throws IOException {
        if (token == null || token.length() < 32 || token.contains("\n") || token.contains("\r")) throw new IllegalArgumentException("Token interno inválido");
        if (timeoutMs < 100 || timeoutMs > 90000) throw new IllegalArgumentException("Tiempo fuera de rango");
        this.tokenDigest = digest("Bearer " + token); this.motor = motor; this.timeoutMs = timeoutMs;
        server = HttpServer.create(bind, 16);
        server.setExecutor(handlers); server.createContext("/", this::handle);
    }
    /** Pequeño margen sobre el plazo del motor: así el motor alcanza a registrar en qué paso se agotó antes de ser cancelado. */
    private long margenMs() { return Math.min(3000, Math.max(10, timeoutMs / 10)); }
    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }
    private void handle(HttpExchange exchange) throws IOException {
        // Closing the exchange also bounds slow body uploads without recording the body.
        ScheduledFuture<?> readDeadline = deadlines.schedule(exchange::close, 10, TimeUnit.SECONDS);
        try {
            String supplied = exchange.getRequestHeaders().getFirst("Authorization");
            if (!MessageDigest.isEqual(tokenDigest, digest(supplied == null ? "" : supplied))) {
                send(exchange, 401, LectorChromium.estado(Estado.NO_AUTORIZADO)); return;
            }
            String path = exchange.getRequestURI().getPath();
            if (exchange.getRequestURI().getQuery() != null) { send(exchange, 404, LectorChromium.estado(Estado.NO_ENCONTRADO)); return; }
            if ("GET".equals(exchange.getRequestMethod()) && "/interno/salud".equals(path)) {
                send(exchange, 200, LectorChromium.estado(Estado.OK)); return;
            }
            String operation = path.startsWith("/interno/") ? path.substring(9) : "";
            if (!"POST".equals(exchange.getRequestMethod()) || !Set.of("sesion", "buscar", "ficha", "url").contains(operation)) {
                send(exchange, 404, LectorChromium.estado(Estado.NO_ENCONTRADO)); return;
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).matches("application/json(?:\\s*;.*)?")) {
                send(exchange, 415, LectorChromium.estado(Estado.SOLICITUD_INVALIDA)); return;
            }
            byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) { send(exchange, 413, LectorChromium.estado(Estado.SOLICITUD_INVALIDA)); return; }
            Peticion request;
            try { request = JSON.readValue(body, Peticion.class); ValidacionLectura.pedido(operation, request); }
            catch (com.genlogs.app.sourcing.lector.LectorRemotoException e) { send(exchange, status(e.getEstado()), LectorChromium.estado(e.getEstado())); return; }
            catch (Exception e) { send(exchange, 400, LectorChromium.estado(Estado.SOLICITUD_INVALIDA)); return; }
            readDeadline.cancel(false);
            if (!slots.tryAcquire()) { send(exchange, 503, LectorChromium.estado(Estado.OCUPADO)); return; }
            long inicio = System.nanoTime();
            Future<Respuesta> future;
            try {
                future = workers.submit(() -> { try { return motor.ejecutar(operation, request); } finally { slots.release(); } });
            } catch (RejectedExecutionException e) { slots.release(); send(exchange, 503, LectorChromium.estado(Estado.OCUPADO)); return; }
            try {
                Respuesta response = future.get(timeoutMs + margenMs(), TimeUnit.MILLISECONDS);
                responderLectura(exchange, operation, request, response, inicio);
            } catch (TimeoutException e) {
                // The worker retains its slot until it closes Chromium, preventing resource accumulation.
                System.out.println("Lector " + operation + " " + request.fuente() + ": el navegador no terminó dentro del plazo del servicio (" + (timeoutMs + margenMs()) + " ms)");
                future.cancel(true);
                responderLectura(exchange, operation, request, LectorChromium.estado(Estado.TIEMPO_AGOTADO), inicio);
            } catch (InterruptedException e) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                responderLectura(exchange, operation, request, LectorChromium.estado(Estado.SIN_RESPUESTA), inicio);
            } catch (ExecutionException e) {
                responderLectura(exchange, operation, request, LectorChromium.estado(Estado.SIN_RESPUESTA), inicio);
            }
        } finally { readDeadline.cancel(false); exchange.close(); }
    }
    private static void responderLectura(HttpExchange exchange, String operacion, Peticion request,
                                         Respuesta response, long inicio) throws IOException {
        int codigo = status(response.estado());
        if ("sesion".equals(operacion)) {
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
            log.info("Lector sesion {}: resultado={} HTTP={} duración={} ms",
                    request.fuente(), response.estado(), codigo, ms);
        }
        send(exchange, codigo, response);
    }
    private static int status(Estado estado) {
        return switch (estado) {
            case SOLICITUD_INVALIDA, URL_NO_PERMITIDA -> 400;
            case NO_AUTORIZADO -> 401; case OCUPADO -> 503;
            case SIN_RESPUESTA -> 502; case TIEMPO_AGOTADO -> 504;
            default -> 200;
        };
    }
    private static void send(HttpExchange exchange, int code, Respuesta response) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(code, bytes.length); exchange.getResponseBody().write(bytes);
    }
    private static byte[] digest(String text) {
        try { return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(); }
    }
    @Override public void close() { server.stop(0); handlers.shutdownNow(); workers.shutdownNow(); deadlines.shutdownNow(); }
    public static void main(String[] args) throws Exception {
        String token = System.getenv("GENLOGS_LECTOR_TOKEN");
        long timeout = Long.parseLong(System.getenv().getOrDefault("GENLOGS_LECTOR_TIMEOUT_MS", "20000"));
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "3001"));
        ServicioLectura servicio = new ServicioLectura(new InetSocketAddress("0.0.0.0", port), token, timeout, new LectorChromium(timeout));
        Runtime.getRuntime().addShutdownHook(new Thread(servicio::close)); servicio.start();
        System.out.println("Servicio de lectura iniciado");
    }
}
