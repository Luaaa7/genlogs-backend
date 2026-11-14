package com.genlogs.app.sourcing;

import org.springframework.stereotype.Component;

/**
 * Espera entre consultas a la misma tienda ({@code fuente_busqueda.pausa_segundos}).
 * Solo la usa el 9-B.1, después de comprobar la cuota y antes de pedir la sesión.
 */
@Component
public class PausaFuente {

    public void esperar(int segundos) {
        if (segundos <= 0) {
            return;
        }
        try {
            Thread.sleep(segundos * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
