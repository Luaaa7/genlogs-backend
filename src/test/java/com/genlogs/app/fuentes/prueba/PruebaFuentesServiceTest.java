package com.genlogs.app.fuentes.prueba;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.EstadoFuente;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfig;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfigRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.sourcing.ConsultaFuente;
import com.genlogs.app.sourcing.ConsultaFuenteService;
import com.genlogs.app.sourcing.EstadoConsultaFuente;
import com.genlogs.app.sourcing.MetodoBusqueda;

/** M2 (BPMN 9-C): la búsqueda de prueba usa el 9-B.1 y una tienda que falla no detiene a las demás. */
class PruebaFuentesServiceTest {

    private FuenteBusquedaConfigRepository fuentes;
    private ConsultaFuenteService consultas;
    private PruebaFuentesService service;

    private FuenteBusquedaConfig ml;
    private FuenteBusquedaConfig ebay;
    private FuenteBusquedaConfig amazon;

    @BeforeEach
    void preparar() {
        fuentes = mock(FuenteBusquedaConfigRepository.class);
        consultas = mock(ConsultaFuenteService.class);
        service = new PruebaFuentesService(fuentes, mock(FuentePruebaRepository.class), consultas,
                mock(UsuarioRepository.class));
        ml = fuente(CodigoFuente.MERCADO_LIBRE, EstadoFuente.ACTIVA);
        ebay = fuente(CodigoFuente.EBAY, EstadoFuente.ACTIVA);
        amazon = fuente(CodigoFuente.AMAZON, EstadoFuente.SIN_PROBAR);
        when(fuentes.findAll()).thenReturn(List.of(amazon, ebay, ml));
    }

    private static FuenteBusquedaConfig fuente(CodigoFuente codigo, EstadoFuente estado) {
        FuenteBusquedaConfig f = new FuenteBusquedaConfig();
        f.setCodigo(codigo);
        f.setNombre(codigo.name());
        f.setEstado(estado);
        return f;
    }

    @Test
    void consultaLasTiendasConSesionEnOrdenYOmiteLasDemas() {
        when(consultas.consultar(eq(ml), any())).thenReturn(
                ConsultaFuente.de("MERCADO_LIBRE", EstadoConsultaFuente.SIN_RESULTADOS, null, List.of()));
        when(consultas.consultar(eq(ebay), any())).thenReturn(
                ConsultaFuente.de("EBAY", EstadoConsultaFuente.OK, null, List.of()));

        List<ConsultaFuente> r = service.buscar(new BusquedaPruebaRequest(" CAT ", " 1R-0750 "));

        assertThat(r).extracting(ConsultaFuente::codigo).containsExactly("MERCADO_LIBRE", "EBAY", "AMAZON");
        assertThat(r).extracting(ConsultaFuente::estado).containsExactly(EstadoConsultaFuente.SIN_RESULTADOS,
                EstadoConsultaFuente.OK, EstadoConsultaFuente.OMITIDA_SESION);
        verify(consultas).consultar(eq(ebay), argThat(c -> c.metodo() == MetodoBusqueda.NUMERO_PARTE
                && "1R-0750".equals(c.numeroParte()) && "CAT".equals(c.marca())));
        verify(consultas, never()).consultar(eq(amazon), any());
    }

    @Test
    void unaTiendaQueFallaSaleSolaConError() {
        when(consultas.consultar(eq(ml), any())).thenThrow(new IllegalStateException("se cayó"));
        when(consultas.consultar(eq(ebay), any())).thenReturn(
                ConsultaFuente.de("EBAY", EstadoConsultaFuente.OK, null, List.of()));

        List<ConsultaFuente> r = service.buscar(new BusquedaPruebaRequest(null, "1R-0750"));

        assertThat(r.get(0).estado()).isEqualTo(EstadoConsultaFuente.CON_ERROR);
        assertThat(r.get(0).motivo()).doesNotContain("se cayó");
        assertThat(r.get(1).estado()).isEqualTo(EstadoConsultaFuente.OK);
    }
}