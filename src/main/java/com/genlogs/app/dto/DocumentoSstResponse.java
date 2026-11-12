package com.genlogs.app.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

/** Un pase o un SCTR en el GET interno. Nulo en el bloque cuando no hay fila activa. */
@Getter
@Builder
public class DocumentoSstResponse {
    private boolean vigente;
    private LocalDate vencimiento;
    private String referencia;
}
