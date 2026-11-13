package com.genlogs.app.fuentes.config;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifrado AES-GCM de las credenciales de las tiendas (K2, v3.3).
 *
 * <ul>
 *   <li>La clave es de 256 bits, en Base64, en la variable GENLOGS_FUENTES_CLAVE
 *       (Render y el .env de cada una). Nunca va en la base ni en el código.</li>
 *   <li>Sin la variable, o con una clave inválida, el backend no arranca y lo dice
 *       en el log.</li>
 *   <li>Cada valor lleva un IV aleatorio de 12 bytes: el mismo texto cifrado dos veces
 *       da resultados distintos.</li>
 *   <li>El cifrado queda ligado a su contexto (tienda y tipo, p. ej. "EBAY:CLAVE"):
 *       copiar el valor de una fila a otra no se descifra.</li>
 * </ul>
 *
 * Formato guardado: "v1:" + Base64(IV de 12 bytes + texto cifrado con su etiqueta).
 * Si se pierde la clave hay que volver a cargar las 5 cuentas: no se cambia sin avisar.
 */
@Component
public class CifradoFuentes {

    private static final Logger log = LoggerFactory.getLogger(CifradoFuentes.class);

    static final String VARIABLE = "GENLOGS_FUENTES_CLAVE";
    private static final String VERSION = "v1:";
    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int BYTES_CLAVE = 32;
    private static final int BYTES_IV = 12;
    private static final int BITS_ETIQUETA = 128;

    private final SecretKey clave;
    private final SecureRandom azar = new SecureRandom();

    public CifradoFuentes(@Value("${genlogs.fuentes.clave:}") String claveBase64) {
        this.clave = leerClave(claveBase64);
    }

    /** Cifra un valor para guardarlo en fuente_credencial.valor_cifrado. */
    public String cifrar(String texto, String contexto) {
        if (texto == null) {
            throw new IllegalArgumentException("No hay valor para cifrar");
        }
        try {
            byte[] iv = new byte[BYTES_IV];
            azar.nextBytes(iv);
            Cipher cifrador = Cipher.getInstance(ALGORITMO);
            cifrador.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(BITS_ETIQUETA, iv));
            cifrador.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));
            byte[] cifrado = cifrador.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            byte[] todo = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
            return VERSION + Base64.getEncoder().encodeToString(todo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar la credencial de " + contexto, e);
        }
    }

    /**
     * Descifra lo guardado. Falla (sin mostrar el valor) si la clave cambió, el dato
     * está dañado o el contexto no es el de esa fila.
     */
    public String descifrar(String guardado, String contexto) {
        if (guardado == null || !guardado.startsWith(VERSION)) {
            throw new IllegalStateException("La credencial de " + contexto + " no tiene el formato cifrado esperado");
        }
        try {
            byte[] todo = Base64.getDecoder().decode(guardado.substring(VERSION.length()));
            if (todo.length <= BYTES_IV) {
                throw new IllegalStateException("La credencial de " + contexto + " está incompleta");
            }
            ByteBuffer buffer = ByteBuffer.wrap(todo);
            byte[] iv = new byte[BYTES_IV];
            buffer.get(iv);
            byte[] cifrado = new byte[buffer.remaining()];
            buffer.get(cifrado);
            Cipher descifrador = Cipher.getInstance(ALGORITMO);
            descifrador.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(BITS_ETIQUETA, iv));
            descifrador.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));
            return new String(descifrador.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo descifrar la credencial de " + contexto
                    + ": la clave " + VARIABLE + " cambió o el dato está dañado");
        }
    }

    /** Valida la clave al arrancar; si no sirve, explica qué falta y detiene el arranque. */
    static SecretKey leerClave(String claveBase64) {
        if (claveBase64 == null || claveBase64.isBlank()) {
            throw fallaDeArranque("Falta la variable " + VARIABLE + ".");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(claveBase64.trim());
        } catch (IllegalArgumentException e) {
            throw fallaDeArranque("La variable " + VARIABLE + " no está en Base64.");
        }
        if (bytes.length != BYTES_CLAVE) {
            throw fallaDeArranque("La variable " + VARIABLE + " debe tener 32 bytes (AES-256) y tiene "
                    + bytes.length + ".");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    private static IllegalStateException fallaDeArranque(String motivo) {
        String mensaje = motivo + " Es la clave que cifra las cuentas de las fuentes de búsqueda; "
                + "sin ella el backend no arranca. Genera una clave de 32 bytes en Base64 y ponla en Render "
                + "y en tu archivo .env (" + VARIABLE + "=...). No la cambies sin avisar a Karla: con otra clave "
                + "no se pueden leer las cuentas guardadas.";
        log.error(mensaje);
        return new IllegalStateException(mensaje);
    }
}
