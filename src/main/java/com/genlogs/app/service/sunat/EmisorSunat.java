package com.genlogs.app.service.sunat;

/**
 * Quien emite el comprobante ante SUNAT. Hay una versión por modo
 * (variable SUNAT_MODO): SIMULADO hoy, PROVEEDOR cuando la empresa contrate
 * un OSE/PSE. Para agregar otro modo basta con otra clase @Component que
 * implemente esta interfaz.
 */
public interface EmisorSunat {

    /** SIMULADO, PROVEEDOR, ... (lo que se pone en SUNAT_MODO). */
    String modo();

    ResultadoEmisionSunat emitir(ComprobanteSunat comprobante);
}