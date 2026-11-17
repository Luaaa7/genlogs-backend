package com.genlogs.app.sourcing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.genlogs.app.fuentes.config.CampoSelector;
import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.PaginaSelector;
import com.genlogs.app.fuentes.config.ResultadoLectura;
import com.genlogs.app.fuentes.config.SelectorFuenteDto;
import com.genlogs.app.fuentes.lectores.AlibabaLector;
import com.genlogs.app.sourcing.lector.ContratoLector;
import com.genlogs.app.sourcing.lector.LectorRemotoClient;

/**
 * KA + A4 (pedido de Mell): el campo precio de una ficha de Alibaba lleva solo el mayor del rango
 * y el rango completo va en la descripción. Así la normalización del 9-B.1 deja el mayor; si el
 * rango llegara al campo precio, la normalización no daría el mayor (toma el primer número o nada).
 */
class AlibabaRangoNormalizacionTest {

    private static final String SESION =
            "{\"validaHasta\":\"2026-11-16T20:00:00Z\",\"storageState\":{\"cookies\":[],\"origins\":[]}}";

    private static List<SelectorFuenteDto> reglas() {
        return List.of(
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.ITEM, ".organic-list-offer"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.NOMBRE, ".title"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.PRECIO, ".price"),
                new SelectorFuenteDto(PaginaSelector.BUSQUEDA, CampoSelector.ENLACE, "a@href"));
    }

    @Test
    void elPrecioQueLlegaANormalizarEsSoloElMayorYElRangoVaEnLaDescripcion() {
        LectorRemotoClient cliente = mock(LectorRemotoClient.class);
        // Lo que devuelve el servicio de lectura con precioRango="mayor": precio 15.00 y, en la
        // descripción, el texto del precio tal como lo muestra Alibaba.
        when(cliente.buscar(any())).thenReturn(new ContratoLector.Respuesta(ContratoLector.Estado.OK, List.of(
                new ContratoLector.Resultado("Fuel filter CAT 1R-0750", "US$12.50-15.00", new BigDecimal("15.00"),
                        "USD", null, "ALIBABA", "https://www.alibaba.com/product-detail/1.html")), null));
        ConsultaBusqueda consulta = new ConsultaBusqueda(MetodoBusqueda.NUMERO_PARTE, "1R-0750", "CAT", null, null, null);

        ResultadoLectura lectura = new AlibabaLector(cliente)
                .buscar(CodigoFuente.ALIBABA, null, SESION, consulta, reglas());

        // Lo que sale del lector: precio con un solo número y el rango en la descripción.
        assertThat(lectura.fichas().get(0).precio()).isEqualTo("15.00");
        assertThat(lectura.fichas().get(0).descripcion()).contains("US$12.50-15.00").contains("mayor");

        // Lo que deja la normalización del 9-B.1 (A4): el mayor, con el rango en la descripción.
        List<ResultadoBusqueda> fichas = NormalizadorFicha.normalizar(lectura.fichas(), consulta);
        assertThat(fichas).hasSize(1);
        assertThat(fichas.get(0).precio()).isEqualByComparingTo("15.00");
        assertThat(fichas.get(0).codigoMoneda()).isEqualTo("USD");
        assertThat(fichas.get(0).descripcion()).contains("US$12.50-15.00");
    }

    @Test
    void siElRangoLlegaraAlCampoPrecioLaNormalizacionNoDariaElMayor() {
        // Por eso el rango nunca va en el campo precio: a la normalización no le toca elegir.
        assertThat(NormalizadorFicha.precio("US$12.50-15.00")).isNotEqualTo(new BigDecimal("15.00"));
        assertThat(NormalizadorFicha.precio("15.00")).isEqualByComparingTo("15.00");
    }
}
