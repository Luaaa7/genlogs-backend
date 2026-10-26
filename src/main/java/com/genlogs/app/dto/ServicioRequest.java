package com.genlogs.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ServicioRequest {

    @NotBlank(message = "El código del servicio es obligatorio")
    private String codigoServicio;

    @NotBlank(message = "El nombre del servicio es obligatorio")
    private String nombreServicio;

    private String descripcion;

    @NotNull(message = "Debe indicar la categoría del servicio")
    private Integer idCategoriaServicio;

    @NotNull(message = "Debe indicar la unidad de medida del servicio")
    private Integer idUnidadMedida;

    private BigDecimal duracionEstimadaHoras;

    private Boolean visibleWeb;

    private List<Long> idsSectores = new ArrayList<>();
}