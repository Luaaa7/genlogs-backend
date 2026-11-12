package com.genlogs.app.fuentes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj de prueba que se puede adelantar. */
class RelojMovible extends Clock {

    private Instant ahora;

    RelojMovible(String instante) {
        this.ahora = Instant.parse(instante);
    }

    void avanzar(Duration tiempo) {
        ahora = ahora.plus(tiempo);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        RelojMovible base = this;
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return zona;
            }

            @Override
            public Clock withZone(ZoneId otra) {
                return base.withZone(otra);
            }

            @Override
            public Instant instant() {
                return base.instant();
            }
        };
    }

    @Override
    public Instant instant() {
        return ahora;
    }
}
