package com.genlogs.app.dto;

/**
 * Respuesta de GET /api/public/ruc/{ruc}. Solo la razón social (RU3).
 * {@code razonSocial} null es «sin dato»: el formulario la deja escribir a mano (RU5).
 * No es un error y no es 404.
 */
public record RazonSocialPublicaResponse(String razonSocial) {
}
