package com.genlogs.app.service;

import java.util.Locale;
import java.util.Set;

import org.springframework.web.multipart.MultipartFile;

import com.genlogs.app.exception.BusinessException;

/**
 * Evidencia de la respuesta del cliente: PDF, PNG o JPG, hasta 5 MB, nombre saneado.
 */
final class AdjuntoEvidencia {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final String TIPO_NO_PERMITIDO = "El adjunto solo puede ser PDF, PNG o JPG";
    static final String DEMASIADO_GRANDE = "El adjunto no puede superar 5 MB";
    static final String VACIO = "El adjunto está vacío";

    private static final Set<String> EXTENSIONES = Set.of("pdf", "png", "jpg", "jpeg");

    private AdjuntoEvidencia() {
    }

    static boolean presente(MultipartFile archivo) {
        return archivo != null && !archivo.isEmpty();
    }

    /** Nombre seguro y bytes ya validados. */
    static Preparado preparar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException(VACIO);
        }
        long tamanio = archivo.getSize();
        if (tamanio > MAX_BYTES) {
            throw new BusinessException(DEMASIADO_GRANDE);
        }
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (Exception e) {
            throw new BusinessException("No se pudo leer el adjunto");
        }
        if (bytes.length == 0) {
            throw new BusinessException(VACIO);
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessException(DEMASIADO_GRANDE);
        }
        String nombre = sanear(archivo.getOriginalFilename());
        String extension = extension(nombre);
        if (!EXTENSIONES.contains(extension) || !coincide(extension, bytes)) {
            throw new BusinessException(TIPO_NO_PERMITIDO);
        }
        return new Preparado(nombre, bytes, extension);
    }

    static String sanear(String original) {
        String nombre = original == null ? "" : original;
        int corte = Math.max(nombre.lastIndexOf('/'), nombre.lastIndexOf('\\'));
        nombre = nombre.substring(corte + 1).trim();
        nombre = nombre.replaceAll("[^A-Za-z0-9._-]", "_");
        nombre = nombre.replaceAll("_+", "_");
        nombre = nombre.replaceAll("^[._]+", "");
        if (nombre.length() > 120) {
            String extension = extension(nombre);
            int tope = extension.isEmpty() ? 120 : 119 - extension.length();
            if (tope < 1) {
                tope = 1;
            }
            String base = nombre.substring(0, Math.min(nombre.length(), tope));
            nombre = extension.isEmpty() ? base : base + "." + extension;
        }
        if (nombre.isBlank() || extension(nombre).isEmpty()) {
            throw new BusinessException(TIPO_NO_PERMITIDO);
        }
        return nombre;
    }

    private static String extension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        if (punto < 0 || punto == nombre.length() - 1) {
            return "";
        }
        return nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean coincide(String extension, byte[] bytes) {
        if ("pdf".equals(extension)) {
            return esPdf(bytes);
        }
        if ("png".equals(extension)) {
            return esPng(bytes);
        }
        return esJpg(bytes);
    }

    private static boolean esPdf(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F';
    }

    private static boolean esPng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G';
    }

    private static boolean esJpg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    record Preparado(String nombre, byte[] bytes, String extension) {
        boolean esImagen() {
            return !"pdf".equals(extension);
        }
    }
}
