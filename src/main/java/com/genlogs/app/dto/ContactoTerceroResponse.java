package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactoTerceroResponse {
    private Long idContacto;
    private Long idTercero;
    private String nombres;
    private String cargo;
    private String correo;
    private String telefono;
    private Boolean esPrincipal;
}