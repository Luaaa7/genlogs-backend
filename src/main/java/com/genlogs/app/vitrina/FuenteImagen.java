package com.genlogs.app.vitrina;

/**
 * producto_imagen.fuente_imagen (v3_2.sql, RF-79). Al público solo se muestran
 * fotos propias: tomada en la recepción, del proveedor con permiso o del cliente.
 */
public enum FuenteImagen {
    RECEPCION,
    PROVEEDOR,
    CLIENTE
}
