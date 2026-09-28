package com.genlogs.app.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CotizacionRequest {

    @NotNull(message = "Debe indicar el cliente")
    private Long idCliente;

    @NotNull(message = "Debe indicar el contacto")
    private Long idContacto;

    @NotNull(message = "Debe indicar la moneda")
    private Integer idMoneda;

    @NotNull(message = "Debe indicar la condición de pago")
    private Integer idCondicionPago;

    private Integer idSectorEconomico;

    @NotEmpty(message = "La cotización debe tener al menos una línea")
    @Valid
    private List<CotizacionDetalleRequest> lineas;
}