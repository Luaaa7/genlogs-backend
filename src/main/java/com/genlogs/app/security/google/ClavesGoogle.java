package com.genlogs.app.security.google;

import java.security.Key;

/** Claves públicas con las que Google firma sus tokens, por su identificador (kid). */
@FunctionalInterface
public interface ClavesGoogle {

    /** La clave del kid indicado, o null si Google no la publica. */
    Key clave(String kid);
}
