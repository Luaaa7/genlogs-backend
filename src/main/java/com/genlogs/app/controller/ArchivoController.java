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

import lombok.RequiredArgsConstructor;

/** Sube un archivo a Cloudinary y devuelve su URL. El frontend la asocia después al producto/cotización. */
@RestController
@RequestMapping("/api/archivos")
@RequiredArgsConstructor
public class ArchivoController {

    private static final String CARPETA = "genlogs/archivos";

    private final CloudinaryService cloudinaryService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ArchivoSubidoResponse> subir(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("El archivo está vacío");
        }

        String tipo = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String url = tipo.startsWith("image/")
                ? cloudinaryService.subirImagen(file, CARPETA)
                : cloudinaryService.subirDocumento(file, CARPETA);

        return ResponseEntity.ok(new ArchivoSubidoResponse(url, file.getOriginalFilename(), tipo, file.getSize()));
    }
}