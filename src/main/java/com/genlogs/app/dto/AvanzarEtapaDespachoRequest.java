package com.genlogs.app.dto;

import com.genlogs.app.enums.EtapaDespacho;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AvanzarEtapaDespachoRequest {

    @NotNull(message = "Indica la etapa")
    private EtapaDespacho etapa;
}
