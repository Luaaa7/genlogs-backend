package com.genlogs.app.dto;

import java.time.LocalDateTime;

import com.genlogs.app.enums.EtapaDespacho;
import com.genlogs.app.enums.OrigenRepuesto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DespachoResponse {
    private Long idDespacho;
    private Long idOrdenCompra;
    private String numeroOrdenCompra;
    private Long idCotizacion;
    private OrigenRepuesto origen;
    private EtapaDespacho etapa;
    private String etapaNombre;
    private LocalDateTime fechaPreparacion;
    private LocalDateTime fechaAduana;
    private LocalDateTime fechaTransito;
    private LocalDateTime fechaEntrega;
    private LocalDateTime plazoDesde;
    private LocalDateTime plazoHasta;
    private Long idUsuario;
    /** True si algún ítem no se pudo determinar. No sale en el portal. */
    private boolean origenIncompleto;
}
