package com.genlogs.app.enums;

/**
 * Etapas del despacho. Solo se avanza a la siguiente, sin saltos ni retroceso.
 * EN_ADUANA existe únicamente cuando el origen es IMPORTADO.
 */
public enum EtapaDespacho {
    REGISTRADO("Registrado"),
    EN_PREPARACION("En preparación"),
    EN_ADUANA("En aduana"),
    EN_TRANSITO("En tránsito"),
    ENTREGADO("Entregado");

    private final String etiqueta;

    EtapaDespacho(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** La etapa que sigue, o nulo si ya está entregado. */
    public EtapaDespacho siguiente(OrigenRepuesto origen) {
        return switch (this) {
            case REGISTRADO -> EN_PREPARACION;
            case EN_PREPARACION -> origen == OrigenRepuesto.IMPORTADO ? EN_ADUANA : EN_TRANSITO;
            case EN_ADUANA -> EN_TRANSITO;
            case EN_TRANSITO -> ENTREGADO;
            case ENTREGADO -> null;
        };
    }
}
