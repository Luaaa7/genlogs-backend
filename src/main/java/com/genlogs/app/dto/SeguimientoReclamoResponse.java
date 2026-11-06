package com.genlogs.app.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeguimientoReclamoResponse {
    private Long idSeguimientoReclamo;
    private String estadoAnterior;
    private String estadoNuevo;
    private Long idUsuario;
    private String usuario;
    private LocalDateTime fechaEvento;
    private String comentario;
}
