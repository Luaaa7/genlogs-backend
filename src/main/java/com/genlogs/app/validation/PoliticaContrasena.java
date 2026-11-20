package com.genlogs.app.validation;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Reglas de la contraseña del portal (CO5, CO6). Están juntas a propósito:
 * el grupo todavía las tiene «por confirmar» y se cambian solo aquí.
 * El mensaje dice qué regla falta y nunca incluye la contraseña.
 */
public final class PoliticaContrasena {

    public static final int MINIMO = 8;
    public static final int MAXIMO = 72;
    /** El tope es de BCrypt y se mide en bytes UTF-8: las tildes y la ñ ocupan 2. */
    public static final String MENSAJE_DEMASIADO_LARGA =
            "La contraseña es demasiado larga (máximo 72 bytes; las tildes y la ñ cuentan doble)";

    private PoliticaContrasena() {
    }

    /** Vacío si cumple. Si no, el texto de la primera regla que falta. */
    public static Optional<String> incumplimiento(String password, String correo) {
        if (password == null || password.isBlank()) {
            return Optional.of("La contraseña es obligatoria");
        }
        if (password.length() < MINIMO) {
            return Optional.of("La contraseña debe tener al menos " + MINIMO + " caracteres");
        }
        // String.length() cuenta caracteres (unidades UTF-16). BCrypt rechaza más de 72 bytes UTF-8
        // y, sin esta segunda medida, 40 "ñ" (80 bytes) más las otras reglas pasaban y el alta terminaba en 500.
        if (password.length() > MAXIMO || password.getBytes(StandardCharsets.UTF_8).length > MAXIMO) {
            return Optional.of(MENSAJE_DEMASIADO_LARGA);
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            return Optional.of("La contraseña debe incluir una mayúscula");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            return Optional.of("La contraseña debe incluir una minúscula");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            return Optional.of("La contraseña debe incluir un número");
        }
        if (correo != null && password.equalsIgnoreCase(correo.trim())) {
            return Optional.of("La contraseña debe ser distinta del correo");
        }
        return Optional.empty();
    }
}