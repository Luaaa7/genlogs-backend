package com.genlogs.app.vitrina.dto;

import com.genlogs.app.enums.OrigenRepuesto;
import com.genlogs.app.enums.TipoRepuesto;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Convertir una referencia ÚTIL del historial en producto de la vitrina (BPMN 10).
 * Categoría y unidad de medida son obligatorias (D-22).
 *
 * @param costoPuestoLima opcional: si no viene, se usa el de la referencia
 * @param margen          opcional: si no viene, se usa MARGEN_REP_SUGERIDO (D-17)
 */
public record ConvertirProductoRequest(
        @NotNull(message = "Indica la referencia que se convierte") Long idSourcing,
        @NotBlank(message = "Escribe el nombre del repuesto") @Size(max = 200, message = "Máximo 200 caracteres") String nombre,
        @Size(max = 4000, message = "Máximo 4000 caracteres") String descripcion,
        @NotNull(message = "Elige la categoría") Integer idCategoriaProducto,
        @NotNull(message = "Elige la unidad de medida") Integer idUnidadMedida,
        Integer idMarca,
        @Size(max = 60, message = "Máximo 60 caracteres") String numeroParte,
        @NotNull(message = "Indica si es Original (OEM) o Alternativo") TipoRepuesto tipoRepuesto,
        OrigenRepuesto origen,
        @Positive(message = "El costo debe ser mayor a cero") BigDecimal costoPuestoLima,
        @DecimalMin(value = "0", message = "El margen no puede ser negativo")
        @DecimalMax(value = "100", message = "El margen no puede pasar de 100 %") BigDecimal margen) {
}
