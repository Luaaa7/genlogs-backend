package com.genlogs.app.validation;

import java.util.Optional;

/**
 * Reglas de la contraseña del portal (CO5, CO6). Están juntas a propósito:
 * el grupo todavía las tiene «por confirmar» y se cambian solo aquí.
 * El mensaje dice qué regla falta y nunca incluye la contraseña.
 */
public final class PoliticaContrasena {

    public static final int MINIMO = 8;
    public static final int MAXIMO = 72;

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
        if (password.length() > MAXIMO) {
            return Optional.of("La contraseña no puede tener más de " + MAXIMO + " caracteres");
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
