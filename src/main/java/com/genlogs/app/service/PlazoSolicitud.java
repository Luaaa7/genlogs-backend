package com.genlogs.app.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.genlogs.app.enums.TipoPlazo;

/**
 * Plazo interno de una solicitud (RF-84): días hábiles según el tipo, tomados de
 * parametro_sistema, y fecha límite con el calendario hábil (horario y feriados).
 */
@Component
public class PlazoSolicitud {

    private final ParametrosService parametrosService;
    private final CalendarioHabil calendarioHabil;

    public PlazoSolicitud(ParametrosService parametrosService, CalendarioHabil calendarioHabil) {
        this.parametrosService = parametrosService;
        this.calendarioHabil = calendarioHabil;
    }

    public LocalDateTime fechaLimite(LocalDateTime recibido, TipoPlazo tipo) {
        return calendarioHabil.fechaLimite(recibido, diasDePlazo(tipo));
    }

    /** Días hábiles del parámetro; si falta la fila se usa el valor de v3_2.sql para no frenar el pedido. */
    public int diasDePlazo(TipoPlazo tipo) {
        String clave = switch (tipo) {
            case COMUN -> ClavesParametro.PLAZO_COMUN_DIAS;
            case IMPORTADO -> ClavesParametro.PLAZO_IMPORTADO_DIAS;
            case ESPECIAL -> ClavesParametro.PLAZO_ESPECIAL_DIAS;
        };
        int porDefecto = switch (tipo) {
            case COMUN -> 1;
            case IMPORTADO -> 3;
            case ESPECIAL -> 5;
        };
        return parametrosService.buscar(clave).map(valor -> {
            try {
                int dias = Integer.parseInt(valor);
                return dias >= 1 ? dias : porDefecto;
            } catch (NumberFormatException e) {
                return porDefecto;
            }
        }).orElse(porDefecto);
    }
}
