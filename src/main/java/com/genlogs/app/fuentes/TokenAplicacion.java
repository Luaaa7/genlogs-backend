package com.genlogs.app.fuentes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Token de aplicación (OAuth client credentials) guardado en memoria hasta poco antes de
 * vencer. eBay limita cuántos tokens se piden al día, así que no se pide uno por búsqueda.
 */
public class TokenAplicacion {

    /** Se renueva este tiempo antes de que venza, para no usar uno a punto de expirar. */
    static final Duration MARGEN = Duration.ofMinutes(5);

    private final Supplier<Emitido> emisor;
    private final Clock reloj;

    private String valor;
    private Instant vence;

    public TokenAplicacion(Supplier<Emitido> emisor, Clock reloj) {
        this.emisor = emisor;
        this.reloj = reloj;
    }

    public synchronized String obtener() {
        Instant ahora = reloj.instant();
        if (valor == null || vence == null || !ahora.isBefore(vence.minus(MARGEN))) {
            Emitido nuevo = emisor.get();
            valor = nuevo.valor();
            vence = ahora.plusSeconds(Math.max(nuevo.segundosVigencia(), 0));
        }
        return valor;
    }

    /** Descartar el token (p. ej. la plataforma respondió 401). */
    public synchronized void invalidar() {
        valor = null;
        vence = null;
    }

    /** Token recién emitido y cuántos segundos dura. */
    public record Emitido(String valor, long segundosVigencia) {
    }
}
