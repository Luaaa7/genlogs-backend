package com.genlogs.app.dto;

import jakarta.validation.constraints.Size;

/** Cuerpo de PATCH /api/portal/pedidos/{id}/cancelar. El motivo es opcional. */
public record CancelarPedidoRequest(
        @Size(max = 300, message = "El motivo puede tener hasta 300 caracteres")
        String motivo) {
}
