package com.genlogs.app.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.genlogs.app.enums.EtapaDespacho;

import lombok.Builder;
import lombok.Getter;

/** Lo que ve el cliente. Sin usuario, fechas por etapa, auditoría ni origen incompleto. */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PortalDespachoResponse {
    private Long id;
    private EtapaDespacho etapa;
    private String etapaNombre;
    private LocalDateTime plazoDesde;
    private LocalDateTime plazoHasta;
    /** N.º interno de la orden, formato 0031-10. */
    private String numeroOrdenCompra;
}
