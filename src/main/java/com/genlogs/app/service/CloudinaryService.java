package com.genlogs.app.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.genlogs.app.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Encapsula la subida y eliminación de archivos en Cloudinary. Es genérico
 * (la carpeta la decide quien lo llama) para que lo reutilicen los demás
 * módulos: imágenes de producto y de servicio, documentos de producto y
 * adjuntos de cotización.
 */
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    /** resource_type de Cloudinary para imágenes. */
    public static final String TIPO_IMAGEN = "image";
    /** resource_type de Cloudinary para PDF, Word, Excel, etc. */
    public static final String TIPO_DOCUMENTO = "raw";

    private final Cloudinary cloudinary;

    /** Verifica si las credenciales de Cloudinary están presentes y no vacías. */
    public boolean estaConfigurado() {
        if (cloudinary == null || cloudinary.config == null) {
            return false;
        }
        String cloudName = cloudinary.config.cloudName;
        String apiKey = cloudinary.config.apiKey;
        String apiSecret = cloudinary.config.apiSecret;
        return cloudName != null && !cloudName.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank();
    }

    /** Sube una imagen a la carpeta indicada y devuelve la URL https segura. */
    public String subirImagen(MultipartFile archivo, String carpeta) {
        return subir(archivo, carpeta, TIPO_IMAGEN);
    }

    /** Sube un documento (PDF, ficha técnica, etc.) a la carpeta indicada y devuelve su URL https. */
    public String subirDocumento(MultipartFile archivo, String carpeta) {
        return subir(archivo, carpeta, TIPO_DOCUMENTO);
    }

    /** Elimina de Cloudinary el archivo al que apunta la URL guardada en la BD. */
    public void eliminarPorUrl(String urlCloudinary, String resourceType) {
        if (urlCloudinary == null || urlCloudinary.isBlank()) {
            return;
        }
        if (!estaConfigurado()) {
            log.warn("Cloudinary no configurado. Se omite eliminación física para URL: {}", urlCloudinary);
            return;
        }
        eliminar(extraerPublicId(urlCloudinary, resourceType), resourceType);
    }

    /** Elimina un recurso de Cloudinary a partir de su public_id. */
    public void eliminar(String publicId, String resourceType) {
        if (!estaConfigurado()) {
            log.warn("Cloudinary no configurado. Se omite eliminación física para publicId: {}", publicId);
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", resourceType));
        } catch (IOException e) {
            log.error("No se pudo eliminar el archivo de Cloudinary: {}", publicId, e);
        }
    }

    /**
     * Extrae el public_id (incluida la carpeta) de una URL de Cloudinary, ej.:
     * https://res.cloudinary.com/<cloud>/image/upload/v169.../genlogs/productos/imagenes/abc123.jpg
     * -> genlogs/productos/imagenes/abc123
     *
     * Las imágenes ("image") se identifican SIN extensión; en cambio, los
     * archivos "raw" (documentos) conservan la extensión dentro del public_id,
     * por eso solo se recorta para imágenes.
     */
    public String extraerPublicId(String urlCloudinary, String resourceType) {
        int idxUpload = urlCloudinary.indexOf("/upload/");
        if (idxUpload < 0) {
            throw new IllegalArgumentException("URL de Cloudinary no válida: " + urlCloudinary);
        }
        String publicId = urlCloudinary
                .substring(idxUpload + "/upload/".length())
                .replaceFirst("^v\\d+/", "");

        if (TIPO_IMAGEN.equals(resourceType)) {
            int punto = publicId.lastIndexOf('.');
            if (punto > publicId.lastIndexOf('/')) {
                publicId = publicId.substring(0, punto);
            }
        }
        return publicId;
    }

    
    private String subir(MultipartFile archivo, String carpeta, String resourceType) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("El archivo está vacío o no es válido");
        }

        if (!estaConfigurado()) {
            throw new BusinessException(
                    "El servicio de almacenamiento de archivos no está configurado");
        }

        try {
            Map<?, ?> resultado = cloudinary.uploader().upload(
                    archivo.getBytes(),
                    ObjectUtils.asMap(
                            "folder", carpeta,
                            "resource_type", resourceType));

            Object secureUrl = resultado.get("secure_url");

            if (!(secureUrl instanceof String url) || url.isBlank()) {
                throw new BusinessException(
                        "Cloudinary no devolvió una URL válida para el archivo");
            }

            return url;

        } catch (IOException e) {
            log.error(
                    "Error al subir archivo a Cloudinary. Tipo de recurso: {}, carpeta: {}",
                    resourceType,
                    carpeta,
                    e);

            throw new BusinessException("No se pudo subir el archivo a Cloudinary");
        }
    }

}

