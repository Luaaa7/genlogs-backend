package com.genlogs.app.sourcing;

/**
 * PROVISIONAL — viene con FuenteBusqueda en el Paso 0. No va en el PR.
 *
 * @param metodo      NUMERO_PARTE, GTIN, PALABRAS o IMAGEN
 * @param numeroParte N.º de parte del fabricante (dato principal, D-01)
 * @param marca       marca en texto
 * @param gtin        opcional
 * @param palabras    texto libre de apoyo
 * @param urlImagen   imagen de apoyo (búsqueda por imagen)
 */
public record ConsultaBusqueda(
        MetodoBusqueda metodo,
        String numeroParte,
        String marca,
        String gtin,
        String palabras,
        String urlImagen) {
}
