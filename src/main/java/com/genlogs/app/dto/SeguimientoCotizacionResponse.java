package com.genlogs.app.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeguimientoCotizacionResponse {
    private Long idSeguimiento;
    private String estado;
    private String usuario;
    private LocalDateTime fechaEvento;
    private String comentario;
}