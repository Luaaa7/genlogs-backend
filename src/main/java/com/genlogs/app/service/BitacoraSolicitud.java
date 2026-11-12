package com.genlogs.app.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.genlogs.app.enums.TipoEventoSolicitud;
import com.genlogs.app.model.SolicitudEvento;
import com.genlogs.app.repository.SolicitudEventoRepository;

/**
 * Bitácora de la solicitud (solicitud_evento, RF-62 y RNF-31): cada cambio queda escrito y no
 * se edita ni se borra. La usan crear, cancelar, pausar, reanudar, reclasificar y reasignar.
 */
@Component
public class BitacoraSolicitud {

    private final SolicitudEventoRepository eventoRepository;

    public BitacoraSolicitud(SolicitudEventoRepository eventoRepository) {
        this.eventoRepository = eventoRepository;
    }

    public void registrar(Long idSolicitud, Long idUsuario, TipoEventoSolicitud tipo, String detalle) {
        SolicitudEvento evento = new SolicitudEvento();
        evento.setIdSolicitud(idSolicitud);
        evento.setIdUsuario(idUsuario);
        evento.setTipoEvento(tipo.name());
        evento.setDetalle(detalle);
        evento.setFechaEvento(LocalDateTime.now());
        eventoRepository.save(evento);
    }
}
