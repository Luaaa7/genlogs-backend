package com.genlogs.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.genlogs.app.dto.ArchivoSubidoResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.service.CloudinaryService;


/** Sube un archivo a Cloudinary y devuelve su URL. El frontend la asocia después al producto/cotización. */
@RestController
@RequestMapping("/api/archivos")
public class ArchivoController {

    private static final String CARPETA = "genlogs/archivos";

    private final CloudinaryService cloudinaryService;

    public ArchivoController(CloudinaryService cloudinaryService) {
        this.cloudinaryService = cloudinaryService;
    }

    private static final java.util.Set<String> TIPOS_PERMITIDOS = java.util.Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private static final java.util.List<String> EXTENSIONES_PROHIBIDAS = java.util.List.of(
            ".exe", ".bat", ".cmd", ".sh", ".js", ".html", ".htm", ".svg", ".php", ".jar", ".msi");

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ArchivoSubidoResponse> subir(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("El archivo está vacío");
        }

        String tipo = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        String nombre = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        // Solo imágenes y documentos de trabajo: nada de .exe, .html, .js, etc.
        if (!TIPOS_PERMITIDOS.contains(tipo) || EXTENSIONES_PROHIBIDAS.stream().anyMatch(nombre::endsWith)) {
            throw new BusinessException("Tipo de archivo no permitido. Sube imágenes (JPG, PNG, WEBP) o documentos (PDF, Word, Excel).");
        }
        String url = tipo.startsWith("image/")
                ? cloudinaryService.subirImagen(file, CARPETA)
                : cloudinaryService.subirDocumento(file, CARPETA);

        return ResponseEntity.ok(new ArchivoSubidoResponse(url, file.getOriginalFilename(), tipo, file.getSize()));
    }
}