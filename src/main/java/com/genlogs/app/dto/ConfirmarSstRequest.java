package com.genlogs.app.dto;

import java.time.LocalDate;

import com.genlogs.app.enums.TipoSstDespacho;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Confirmación de pase o SCTR. La URL del archivo, si viene, ya está subida. */
@Getter
@Setter
public class ConfirmarSstRequest {

    @NotNull(message = "Indica si el documento es PASE o SCTR")
    private TipoSstDespacho tipo;

    @NotNull(message = "La fecha de vencimiento es obligatoria")
    private LocalDate fechaVencimiento;

    private LocalDate fechaEmision;

    private String urlDocumento;

    @Size(max = 120, message = "La referencia admite hasta 120 caracteres")
    private String referencia;
}
