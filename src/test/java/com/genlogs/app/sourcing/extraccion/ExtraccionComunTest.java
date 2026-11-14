package com.genlogs.app.sourcing.extraccion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.genlogs.app.fuentes.config.CampoSelector;
import com.genlogs.app.sourcing.ConsultaBusqueda;
import com.genlogs.app.sourcing.MetodoBusqueda;
import com.genlogs.app.sourcing.ResultadoBusqueda;

/** A4: los 5 lectores entregan las fichas igual, venga de la tienda que venga. */
class ExtraccionComunTest {

    private static final String EBAY = "https://www.ebay.com";

    private static Map<CampoSelector, String> ficha(String nombre, String precio, String enlace) {
        Map<CampoSelector, String> campos = new EnumMap<>(CampoSelector.class);
        if (nombre != null) campos.put(CampoSelector.NOMBRE, nombre);
        if (precio != null) campos.put(CampoSelector.PRECIO, precio);
        if (enlace != null) campos.put(CampoSelector.ENLACE, enlace);
        return campos;
    }

    @Test
    void limpiaLosCamposYNormalizaElPrecio() {
        Map<CampoSelector, String> campos = ficha("  Filtro  de aceite\n CAT 1R-0750 ", "US $1,234.50",
                "/itm/123");
        campos.put(CampoSelector.DESCRIPCION, " Original,   nuevo ");
        campos.put(CampoSelector.IMAGEN, "//i.ebayimg.com/a.jpg");

        FichaExtraida f = ExtraccionComun.ficha(campos, EBAY, "USD");

        assertEquals("Filtro de aceite CAT 1R-0750", f.nombre());
        assertEquals("Original, nuevo", f.descripcion());
        assertEquals(new BigDecimal("1234.50"), f.precio());
        assertEquals("USD", f.codigoMoneda());
        assertEquals("https://www.ebay.com/itm/123", f.enlace());
        assertEquals("https://i.ebayimg.com/a.jpg", f.urlImagen());
    }

    @Test
    void sinPrecioLaFichaSigueConPrecioVacio() {
        FichaExtraida f = ExtraccionComun.ficha(ficha("Bomba", "Consultar", EBAY + "/itm/9"), EBAY, "USD");
        assertNull(f.precio());
        assertNull(f.codigoMoneda());
    }

    @Test
    void laMonedaDeLaFichaMandaSobreLaDeLaTienda() {
        Map<CampoSelector, String> campos = ficha("Sello", "99.90", EBAY + "/itm/5");
        campos.put(CampoSelector.MONEDA, "S/");
        assertEquals("PEN", ExtraccionComun.ficha(campos, EBAY, "USD").codigoMoneda());
    }

    @Test
    void sinNombreOSinEnlaceLaFichaSeDescarta() {
        assertNull(ExtraccionComun.ficha(ficha(null, "$10", "/itm/1"), EBAY, "USD"));
        assertNull(ExtraccionComun.ficha(ficha("   ", "$10", "/itm/1"), EBAY, "USD"));
        assertNull(ExtraccionComun.ficha(ficha("Filtro", "$10", null), EBAY, "USD"));
        assertNull(ExtraccionComun.ficha(null, EBAY, "USD"));
    }

    @Test
    void soloAceptaEnlacesWeb() {
        assertNull(ExtraccionComun.ficha(ficha("Filtro", "$10", "javascript:alert(1)"), EBAY, "USD"));
        assertNull(ExtraccionComun.absoluta("/itm/1", null));
        assertEquals("http://x.com/a", ExtraccionComun.absoluta("http://x.com/a", EBAY));
    }

    @Test
    void respetaElMaximoYNoRepiteEnlaces() {
        List<Map<CampoSelector, String>> leidas = List.of(
                ficha("A", "$1", "/itm/1"),
                ficha("A repetida", "$1", "/itm/1"),
                ficha(null, "$2", "/itm/2"),
                ficha("C", "$3", "/itm/3"),
                ficha("D", "$4", "/itm/4"));

        List<FichaExtraida> fichas = ExtraccionComun.fichas(leidas, EBAY, "USD", 2);

        assertEquals(2, fichas.size());
        assertEquals("A", fichas.get(0).nombre());
        assertEquals("C", fichas.get(1).nombre());
        assertEquals(3, ExtraccionComun.fichas(leidas, EBAY, "USD", 0).size());
        assertTrue(ExtraccionComun.fichas(null, EBAY, "USD", 8).isEmpty());
    }

    @Test
    void pasaAlResultadoDeLosLectoresConLaMarcaYElNumeroDeParteBuscados() {
        FichaExtraida f = ExtraccionComun.ficha(ficha("Filtro", "$12", "/itm/7"), EBAY, "USD");
        ConsultaBusqueda consulta = new ConsultaBusqueda(MetodoBusqueda.NUMERO_PARTE, "1R-0750", "CAT",
                null, null, null);

        ResultadoBusqueda r = f.aResultado(consulta);

        assertEquals("Filtro", r.titulo());
        assertEquals("CAT", r.marca());
        assertEquals("1R-0750", r.numeroParte());
        assertEquals(new BigDecimal("12.00"), r.precio());
        assertEquals("USD", r.codigoMoneda());
        assertEquals("https://www.ebay.com/itm/7", r.urlReferencia());
    }
}