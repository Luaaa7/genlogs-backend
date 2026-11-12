package com.genlogs.app.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Estado SST del despacho interno. {@code bloqueaAvance} es true solo cuando
 * el siguiente paso es salida o entrega y falta un documento vigente.
 */
@Getter
@Builder
public class BloqueoSstResponse {
    private boolean bloqueaAvance;
    private String mensaje;
    private DocumentoSstResponse pase;
    private DocumentoSstResponse sctr;
}
