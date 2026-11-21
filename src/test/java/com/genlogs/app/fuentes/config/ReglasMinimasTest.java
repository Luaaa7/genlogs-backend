package com.genlogs.app.fuentes.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ReglasMinimasTest {

    private static FuenteSelector regla(PaginaSelector pagina, CampoSelector campo, String texto) {
        FuenteSelector s = new FuenteSelector();
        s.setPagina(pagina);
        s.setCampo(campo);
        s.setRegla(texto);
        return s;
    }

    private static List<FuenteSelector> completas() {
        List<FuenteSelector> reglas = new ArrayList<>();
        for (CampoSelector c : ReglasMinimas.BUSQUEDA) {
            reglas.add(regla(PaginaSelector.BUSQUEDA, c, ".algo"));
        }
        for (CampoSelector c : ReglasMinimas.LOGIN) {
            reglas.add(regla(PaginaSelector.LOGIN, c, "#algo"));
        }
        return reglas;
    }

    @Test
    void sinReglasFaltanLasOcho() {
        assertThat(ReglasMinimas.faltantes(List.of()))
                .containsExactly("ITEM", "NOMBRE", "PRECIO", "ENLACE", "USUARIO", "CLAVE", "ENVIAR", "EXITO");
        assertThat(ReglasMinimas.faltantes(null)).hasSize(8);
    }

    @Test
    void conTodasNoFaltaNinguna() {
        assertThat(ReglasMinimas.faltantes(completas())).isEmpty();
    }

    @Test
    void unaReglaVaciaCuentaComoFaltante() {
        List<FuenteSelector> reglas = completas();
        reglas.removeIf(s -> s.getCampo() == CampoSelector.ENLACE);
        reglas.add(regla(PaginaSelector.BUSQUEDA, CampoSelector.ENLACE, "   "));
        assertThat(ReglasMinimas.faltantes(reglas)).containsExactly("ENLACE");
    }

    @Test
    void lasReglasDeFichaNoReemplazanALasDeBusqueda() {
        List<FuenteSelector> reglas = completas();
        reglas.removeIf(s -> s.getCampo() == CampoSelector.PRECIO);
        reglas.add(regla(PaginaSelector.FICHA, CampoSelector.PRECIO, ".precio"));
        assertThat(ReglasMinimas.faltantes(reglas)).containsExactly("PRECIO");
    }

    @Test
    void lasReglasOpcionalesNoSePiden() {
        List<FuenteSelector> reglas = completas();
        reglas.add(regla(PaginaSelector.BUSQUEDA, CampoSelector.IMAGEN, "img@src"));
        assertThat(ReglasMinimas.faltantes(reglas)).isEmpty();
    }
}