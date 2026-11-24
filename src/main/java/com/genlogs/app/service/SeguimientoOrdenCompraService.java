package com.genlogs.app.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.SeguimientoOrdenCompraResponse;
import com.genlogs.app.model.EstadoOrdenCompra;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.model.SeguimientoOrdenCompra;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.SeguimientoOrdenCompraRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SeguimientoOrdenCompraService {

    private final SeguimientoOrdenCompraRepository repository;

    @Transactional
    public void registrar(OrdenCompra orden, EstadoOrdenCompra anterior, Usuario usuario, String tipo, String comentario) {
        SeguimientoOrdenCompra evento = SeguimientoOrdenCompra.builder()
                .ordenCompra(orden)
                .estadoOrdenCompra(orden.getEstadoOrdenCompra())
                .estadoAnterior(anterior)
                .usuario(usuario)
                .tipoEvento(tipo)
                .fechaEvento(LocalDateTime.now())
                .comentario(comentario)
                .build();
        evento.setUserCreate(usuario != null ? usuario.getNombreUsuario() : "SISTEMA");
        evento.setProcessCreate("SEGUIMIENTO_ORDEN_COMPRA");
        repository.save(evento);
    }

    @Transactional(readOnly = true)
    public List<SeguimientoOrdenCompraResponse> listar(Long idOrdenCompra) {
        return repository.findByOrdenCompra(idOrdenCompra).stream().map(this::aRespuesta).toList();
    }

    private SeguimientoOrdenCompraResponse aRespuesta(SeguimientoOrdenCompra s) {
        return SeguimientoOrdenCompraResponse.builder()
                .idSeguimiento(s.getIdSeguimiento())
                .tipoEvento(s.getTipoEvento())
                .estadoAnterior(s.getEstadoAnterior() != null ? s.getEstadoAnterior().getCodigoEstado() : null)
                .estadoNuevo(s.getEstadoOrdenCompra().getCodigoEstado())
                .usuario(s.getUsuario() != null ? s.getUsuario().getNombreUsuario() : null)
                .fechaEvento(s.getFechaEvento() != null ? s.getFechaEvento().atZone(ZoneId.systemDefault()).toInstant() : null)
                .comentario(s.getComentario())
                .build();
    }
}