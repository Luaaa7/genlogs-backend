package com.genlogs.app.fuentes.prueba;

/**
 * Resultado de guardar la prueba en una tienda.
 *
 * @param aprobada     4 o más aciertos de 10 (D-37): ya se puede activar
 * @param desactivada  estaba activa y esta corrida no llegó a 4: se apagó
 */
public record CorridaPruebaResponse(
        String codigo,
        String nombre,
        int corrida,
        int aciertos,
        int pedidos,
        boolean aprobada,
        boolean desactivada) {
}