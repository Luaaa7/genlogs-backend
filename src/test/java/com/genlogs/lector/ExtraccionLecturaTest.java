package com.genlogs.lector;

import com.genlogs.app.sourcing.lector.ContratoLector.*;
import com.genlogs.app.sourcing.lector.LectorRemotoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class ExtraccionLecturaTest {
    static Stream<PaginasPrueba.Caso> tiendas() { return PaginasPrueba.CASOS.stream(); }
    @ParameterizedTest @MethodSource("tiendas")
    void extraeBusquedaYFichaDeCadaTienda(PaginasPrueba.Caso caso) {
        for (boolean buscar : new boolean[]{true, false}) {
            var input = caso.peticion(buscar);
            ValidacionLectura.pedido(buscar ? "buscar" : "ficha", input);
            var rows = LectorChromium.extraer(buscar ? caso.busqueda() : caso.ficha(), input.url(), input, buscar);
            assertEquals(buscar ? 2 : 1, rows.size());
            var row = rows.get(0);
            assertEquals("Filtro CAT 1R-0750", row.nombre());
            assertEquals("Filtro de combustible de prueba", row.descripcion());
            assertEquals(new BigDecimal(caso.esperado()), row.precio());
            assertEquals(caso.moneda(), row.codigoMoneda());
            assertEquals(caso.fuente(), row.fuente());
            assertEquals("https://" + caso.host() + "/producto/1", row.enlace());
            assertEquals("https://" + caso.host() + "/foto.jpg", row.imagen());
        }
    }
    @Test void rangoDePrecioNoSeConvierteEnUnNumeroInventado() {
        var caso = PaginasPrueba.CASOS.get(1);
        var error = assertThrows(LectorRemotoException.class, () -> LectorChromium.extraer(caso.ficha().replace("US $25.99", "US $10-20"), caso.peticion(false).url(), caso.peticion(false), false));
        assertEquals(Estado.REGLAS_INVALIDAS, error.getEstado());
    }
    @Test void sinResultadosSoloConSenalExplicita() {
        var input = PaginasPrueba.CASOS.get(1).peticion(true);
        assertTrue(LectorChromium.extraer("<p id=empty>Sin resultados</p>", input.url(), input, true).isEmpty());
        assertThrows(LectorRemotoException.class, () -> LectorChromium.extraer("<p>Error</p>", input.url(), input, true));
    }
    @Test void limitaResultadosYNoAceptaEnlacesDeOtraFuente() {
        var caso = PaginasPrueba.CASOS.get(1);
        var input = caso.peticion(true);
        var limited = new Peticion(input.fuente(), input.url(), input.reglas(), null, null, 1);
        assertEquals(1, LectorChromium.extraer(caso.busqueda(), input.url(), limited, true).size());
        assertThrows(LectorRemotoException.class, () -> LectorChromium.extraer(caso.ficha().replace("href=\"/producto/1\"", "href=\"https://evil.test/item\""), input.url(), input, false));
    }
    @Test void reglasRechazanCodigoEjecutableYCredencialesEnLecturas() {
        var input = PaginasPrueba.CASOS.get(1).peticion(true);
        var rules = new HashMap<>(input.reglas()); rules.put("script", "alert(1)");
        assertThrows(LectorRemotoException.class, () -> ValidacionLectura.pedido("buscar", new Peticion(input.fuente(), input.url(), rules, null, null, 8)));
        assertThrows(LectorRemotoException.class, () -> ValidacionLectura.pedido("buscar", new Peticion(input.fuente(), input.url(), input.reglas(), null, new Credenciales("user", "secret"), 8)));
    }
}
