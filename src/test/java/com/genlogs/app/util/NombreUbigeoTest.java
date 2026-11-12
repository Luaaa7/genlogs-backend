package com.genlogs.app.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** D-15: Factiliza y el INEI escriben el mismo lugar de formas distintas. */
class NombreUbigeoTest {

    @Test
    void nascaYNazcaSonElMismoLugar() {
        assertEquals("NAZCA", NombreUbigeo.clave("NASCA"));
        assertEquals("NAZCA", NombreUbigeo.clave(" Nazca "));
        assertEquals(NombreUbigeo.clave("ICA", "NASCA", "NASCA"), NombreUbigeo.clave("Ica", "Nazca", "Nazca"));
    }

    @Test
    void ignoraTildesMayusculasSignosYEspacios() {
        assertEquals("SAN JUAN DE LURIGANCHO", NombreUbigeo.clave("San  Juan de Lurigancho"));
        assertEquals("APURIMAC", NombreUbigeo.clave("Apurímac"));
        assertEquals("FERRENAFE", NombreUbigeo.clave("FERREÑAFE"));
        assertEquals("CARMEN DE LA LEGUA REYNOSO", NombreUbigeo.clave("Carmen de la Legua-Reynoso"));
    }

    @Test
    void callaoConElNombreLargoDeSunat() {
        assertEquals("CALLAO", NombreUbigeo.clave("PROV. CONST. DEL CALLAO"));
        assertEquals("CALLAO", NombreUbigeo.clave("Provincia Constitucional del Callao"));
    }

    @Test
    void nuloOVacioNoRompe() {
        assertEquals("", NombreUbigeo.clave((String) null));
        assertEquals("", NombreUbigeo.clave("   "));
    }
}
