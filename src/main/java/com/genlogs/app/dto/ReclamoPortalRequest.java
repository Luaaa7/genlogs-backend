package com.genlogs.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * POST /api/portal/reclamos (RF-70). El cliente solo escribe el asunto, la
 * descripción y, si quiere, la orden de compra de su pedido; el cliente, el
 * tipo (RECLAMO) y la prioridad los pone el backend.
 */
@Getter
@Setter
public class ReclamoPortalRequest {

    @NotBlank(message = "Debe indicar el asunto")
    @Size(max = 200, message = "El asunto no puede superar 200 caracteres")
    private String asunto;

    @NotBlank(message = "Debe describir el reclamo")
    @Size(max = 4000, message = "La descripción no puede superar 4000 caracteres")
    private String descripcion;

    /** Opcional: orden de compra relacionada; debe ser de la misma empresa. */
    private Long idOrdenCompra;
}
