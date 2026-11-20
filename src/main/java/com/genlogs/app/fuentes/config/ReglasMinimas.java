package com.genlogs.app.fuentes.config;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * EF2 (v3.4, BPMN de Mell) · Reglas mínimas de una tienda. Son las mismas que exige
 * el código para trabajar:
 * <ul>
 *   <li>BUSQUEDA: ITEM, NOMBRE, PRECIO y ENLACE (ReglasParaLector.sirveParaBuscar).</li>
 *   <li>LOGIN: USUARIO, CLAVE, ENVIAR y EXITO (LectorSesionRemoto).</li>
 * </ul>
 * Una regla vacía o inactiva cuenta como faltante. El resultado es calculado: no se guarda
 * ni cambia EstadoFuente.
 */
public final class ReglasMinimas {

    static final List<CampoSelector> BUSQUEDA =
            List.of(CampoSelector.ITEM, CampoSelector.NOMBRE, CampoSelector.PRECIO, CampoSelector.ENLACE);
    static final List<CampoSelector> LOGIN =
            List.of(CampoSelector.USUARIO, CampoSelector.CLAVE, CampoSelector.ENVIAR, CampoSelector.EXITO);

    private ReglasMinimas() {
    }

    /** Nombres de las reglas mínimas que faltan, primero las de búsqueda y luego las de ingreso. */
    public static List<String> faltantes(List<FuenteSelector> activas) {
        Set<CampoSelector> busqueda = EnumSet.noneOf(CampoSelector.class);
        Set<CampoSelector> login = EnumSet.noneOf(CampoSelector.class);
        if (activas != null) {
            for (FuenteSelector s : activas) {
                if (s == null || s.getCampo() == null || s.getRegla() == null || s.getRegla().isBlank()) {
                    continue;
                }
                if (s.getPagina() == PaginaSelector.BUSQUEDA) {
                    busqueda.add(s.getCampo());
                } else if (s.getPagina() == PaginaSelector.LOGIN) {
                    login.add(s.getCampo());
                }
            }
        }
        List<String> faltan = new ArrayList<>();
        BUSQUEDA.stream().filter(c -> !busqueda.contains(c)).forEach(c -> faltan.add(c.name()));
        LOGIN.stream().filter(c -> !login.contains(c)).forEach(c -> faltan.add(c.name()));
        return faltan;
    }
}