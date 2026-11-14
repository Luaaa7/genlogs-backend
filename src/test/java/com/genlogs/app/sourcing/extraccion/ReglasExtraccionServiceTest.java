package com.genlogs.app.sourcing.extraccion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.genlogs.app.fuentes.config.CampoSelector;
import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.FuenteSelector;
import com.genlogs.app.fuentes.config.FuenteSelectorRepository;
import com.genlogs.app.fuentes.config.PaginaSelector;

/** A4: las reglas salen de fuente_selector en cada búsqueda. */
class ReglasExtraccionServiceTest {

    private FuenteSelectorRepository repo;
    private ReglasExtraccionService service;

    @BeforeEach
    void preparar() {
        repo = mock(FuenteSelectorRepository.class);
        service = new ReglasExtraccionService(repo);
    }

    private static FuenteSelector regla(PaginaSelector pagina, CampoSelector campo, String regla) {
        FuenteSelector s = new FuenteSelector();
        s.setPagina(pagina);
        s.setCampo(campo);
        s.setRegla(regla);
        return s;
    }

    @Test
    void agrupaLasReglasActivasPorPaginaYCampo() {
        when(repo.findByFuente_CodigoAndStatus(CodigoFuente.EBAY, "A")).thenReturn(List.of(
                regla(PaginaSelector.BUSQUEDA, CampoSelector.ITEM, "li.s-item"),
                regla(PaginaSelector.BUSQUEDA, CampoSelector.NOMBRE, " .s-item__title "),
                regla(PaginaSelector.BUSQUEDA, CampoSelector.ENLACE, "a.s-item__link@href"),
                regla(PaginaSelector.FICHA, CampoSelector.PRECIO, ".x-price-primary")));

        ReglasExtraccion r = service.reglasDe(CodigoFuente.EBAY);

        assertEquals(CodigoFuente.EBAY, r.fuente());
        assertEquals(".s-item__title", r.regla(PaginaSelector.BUSQUEDA, CampoSelector.NOMBRE));
        assertEquals(".x-price-primary", r.regla(PaginaSelector.FICHA, CampoSelector.PRECIO));
        assertEquals(3, r.de(PaginaSelector.BUSQUEDA).size());
        assertTrue(r.sirveParaBuscar());
    }

    @Test
    void ignoraReglasVaciasYSinItemNoSirveParaBuscar() {
        when(repo.findByFuente_CodigoAndStatus(CodigoFuente.AMAZON, "A")).thenReturn(List.of(
                regla(PaginaSelector.BUSQUEDA, CampoSelector.ITEM, "   "),
                regla(PaginaSelector.BUSQUEDA, CampoSelector.NOMBRE, "h2"),
                regla(PaginaSelector.BUSQUEDA, CampoSelector.ENLACE, "a@href")));

        ReglasExtraccion r = service.reglasDe(CodigoFuente.AMAZON);

        assertNull(r.regla(PaginaSelector.BUSQUEDA, CampoSelector.ITEM));
        assertFalse(r.sirveParaBuscar());
    }

    @Test
    void sinReglasDevuelveVacioSinFallar() {
        when(repo.findByFuente_CodigoAndStatus(CodigoFuente.ALIBABA, "A")).thenReturn(List.of());

        ReglasExtraccion r = service.reglasDe(CodigoFuente.ALIBABA);

        assertTrue(r.de(PaginaSelector.BUSQUEDA).isEmpty());
        assertTrue(r.de(PaginaSelector.FICHA).isEmpty());
        assertFalse(r.sirveParaBuscar());
    }
}