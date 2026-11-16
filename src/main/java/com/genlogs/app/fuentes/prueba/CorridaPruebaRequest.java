package com.genlogs.app.fuentes.prueba;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Lo que marcó el Jefe en la pantalla Búsqueda de prueba (M3): por cada pedido, si cada
 * tienda acertó. Cada tienda que viene debe traer los 10 pedidos.
 */
public record CorridaPruebaRequest(
        @NotEmpty(message = "Envía los 10 pedidos de la prueba")
        @Size(max = 10, message = "La prueba es de 10 pedidos")
        List<@Valid @NotNull PedidoPrueba> pedidos) {

    public record PedidoPrueba(
            @NotBlank(message = "Indica el pedido")
            @Size(max = 120, message = "El pedido no puede pasar de 120 caracteres")
            String pedido,
            @NotEmpty(message = "Marca al menos una tienda en cada pedido")
            List<@Valid @NotNull ResultadoTienda> resultados) {
    }

    public record ResultadoTienda(
            @NotBlank(message = "Indica la tienda")
            String fuente,
            @NotNull(message = "Indica si la tienda acertó")
            Boolean acerto,
            @Size(max = 2000, message = "El enlace no puede pasar de 2000 caracteres")
            String enlace) {
    }
}