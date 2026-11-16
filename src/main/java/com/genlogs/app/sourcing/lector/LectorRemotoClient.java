package com.genlogs.app.sourcing.lector;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genlogs.app.sourcing.lector.ContratoLector.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** P2: integración optativa, sin cambiar los lectores existentes ni el interruptor de búsqueda. */
@Component
public class LectorRemotoClient {
    private static final int MAX_RESPUESTA = 2 * 1024 * 1024;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private final URI base;
    private final String token;
    private final long timeoutMs;

    public LectorRemotoClient(
            @Value("${GENLOGS_LECTOR_URL:}") String url,
            @Value("${GENLOGS_LECTOR_TOKEN:}") String token,
            @Value("${GENLOGS_LECTOR_TIMEOUT_TOTAL_MS:45000}") long timeoutMs) {
        this.token = token;
        this.timeoutMs = timeoutMs;
        if (timeoutMs < 100 || timeoutMs > 120000) throw new IllegalArgumentException("Tiempo del lector fuera de rango");
        this.base = validarBase(url);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private static URI validarBase(String value) {
        if (value == null || value.isBlank()) return null;
        URI uri;
        try { uri = URI.create(value); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("URL del lector inválida"); }
        if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("URL del lector inválida: usa únicamente el origen del servicio interno");
        }
        return uri;
    }

    public Respuesta iniciarSesion(Peticion peticion) { return ejecutar("sesion", peticion, false); }
    public Respuesta buscar(Peticion peticion) { return ejecutar("buscar", peticion, true); }
    public Respuesta leerFicha(Peticion peticion) { return ejecutar("ficha", peticion, true); }
    public Respuesta leerUrl(Peticion peticion) { return ejecutar("url", peticion, true); }

    private Respuesta ejecutar(String operacion, Peticion peticion, boolean reintentar) {
        if (base == null || token == null || token.length() < 32) throw new LectorRemotoException(Estado.NO_CONFIGURADO);
        final byte[] body;
        try {
            if (peticion == null) throw new IllegalArgumentException();
            body = json.writeValueAsBytes(peticion);
            if (body.length > 512 * 1024) throw new IllegalArgumentException();
        } catch (Exception e) { throw new LectorRemotoException(Estado.SOLICITUD_INVALIDA); }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        int intentos = reintentar ? 2 : 1;
        for (int intento = 0; intento < intentos; intento++) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new LectorRemotoException(Estado.TIEMPO_AGOTADO);
            // Both attempts, including the response body, share one overall deadline.
            long budget = Math.max(1, remaining / (intentos - intento));
            HttpRequest request = HttpRequest.newBuilder(base.resolve("/interno/" + operacion))
                    .timeout(Duration.ofNanos(budget)).header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            CompletableFuture<HttpResponse<byte[]>> future = http.sendAsync(request, info -> new LimitedBody());
            try {
                HttpResponse<byte[]> response = future.get(budget, TimeUnit.NANOSECONDS);
                int status = response.statusCode();
                if (status == 401 || status == 403) throw new LectorRemotoException(Estado.NO_AUTORIZADO);
                boolean transientFailure = status == 502 || status == 503 || status == 504;
                if (transientFailure && intento + 1 < intentos) continue;
                if (status >= 300 && status < 400) throw new LectorRemotoException(Estado.RESPUESTA_INVALIDA);
                Respuesta parsed;
                try { parsed = json.readValue(response.body(), Respuesta.class); }
                catch (Exception e) {
                    throw new LectorRemotoException(transientFailure ? Estado.SIN_RESPUESTA : Estado.RESPUESTA_INVALIDA);
                }
                if (parsed == null || parsed.estado() == null || parsed.resultados() == null) {
                    throw new LectorRemotoException(Estado.RESPUESTA_INVALIDA);
                }
                if (parsed.estado() != Estado.OK) throw new LectorRemotoException(parsed.estado());
                if (status != 200 || ("sesion".equals(operacion) && parsed.sesion() == null)) {
                    throw new LectorRemotoException(Estado.RESPUESTA_INVALIDA);
                }
                return parsed;
            } catch (InterruptedException e) {
                future.cancel(true); Thread.currentThread().interrupt();
                throw new LectorRemotoException(Estado.INTERRUMPIDO);
            } catch (TimeoutException | ExecutionException e) {
                future.cancel(true);
                if (intento + 1 == intentos) {
                    boolean timeout = e instanceof TimeoutException || e.getCause() instanceof java.net.http.HttpTimeoutException;
                    throw new LectorRemotoException(timeout ? Estado.TIEMPO_AGOTADO : Estado.SIN_RESPUESTA);
                }
            }
        }
        throw new LectorRemotoException(Estado.SIN_RESPUESTA);
    }

    /** Bounds memory before accumulating an untrusted response. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private int received;
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                received += buffer.remaining();
                if (received > MAX_RESPUESTA) {
                    subscription.cancel(); delegate.onError(new IllegalStateException("Respuesta demasiado grande")); return;
                }
            }
            delegate.onNext(buffers);
        }
        @Override public void onError(Throwable error) { delegate.onError(error); }
        @Override public void onComplete() { delegate.onComplete(); }
    }
}
