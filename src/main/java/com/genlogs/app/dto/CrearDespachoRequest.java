package com.genlogs.app.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrearDespachoRequest {

    @NotNull(message = "Indica la orden de compra")
    private Long idOrdenCompra;
}
