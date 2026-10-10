package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ArchivoSubidoResponse {
    private String url;
    private String nombreArchivo;
    private String tipoArchivo;
    private long tamanioBytes;
}