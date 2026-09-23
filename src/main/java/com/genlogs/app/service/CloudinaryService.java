package com.genlogs.app.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
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

    /** resource_type de Cloudinary para imágenes. */
    public static final String TIPO_IMAGEN = "image";
    /** resource_type de Cloudinary para PDF, Word, Excel, etc. */
    public static final String TIPO_DOCUMENTO = "raw";

    private final Cloudinary cloudinary;

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
        eliminar(extraerPublicId(urlCloudinary, resourceType), resourceType);
    }

    /** Elimina un recurso de Cloudinary a partir de su public_id. */
    public void eliminar(String publicId, String resourceType) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", resourceType));
        } catch (IOException e) {
            throw new RuntimeException("No se pudo eliminar el archivo de Cloudinary: " + publicId, e);
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
        try {
            Map<?, ?> resultado = cloudinary.uploader().upload(
                    archivo.getBytes(),
                    ObjectUtils.asMap(
                            "folder", carpeta,
                            "resource_type", resourceType));
            return (String) resultado.get("secure_url");
        } catch (IOException e) {
            throw new RuntimeException("No se pudo subir el archivo a Cloudinary", e);
        }
    }
}
