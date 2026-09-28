package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {
    private Long idUsuario;
    private String nombreUsuario;
    private String nombres;
    private String correo;
    private String iniciales;
    private String nombreRol;
    private Integer idRol;
    private Boolean bloqueado;
    private Short intentosFallidos;
}