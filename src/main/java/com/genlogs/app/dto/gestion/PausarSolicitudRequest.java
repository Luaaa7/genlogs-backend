package com.genlogs.app.dto.gestion;

import java.time.LocalDate;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pausa por tercero: motivo corto (proveedor, taller, aduana…) y fecha estimada opcional. */
public record PausarSolicitudRequest(
        @NotBlank(message = "Indica por qué se pausa")
        @Size(max = 40, message = "El motivo puede tener hasta 40 caracteres")
        String motivo,
        @FutureOrPresent(message = "La fecha estimada no puede ser pasada")
        LocalDate fechaEstimada) {
}
