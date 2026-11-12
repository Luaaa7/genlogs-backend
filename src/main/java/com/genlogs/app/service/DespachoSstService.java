package com.genlogs.app.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.BloqueoSstResponse;
import com.genlogs.app.dto.ConfirmarSstRequest;
import com.genlogs.app.dto.DocumentoSstResponse;
import com.genlogs.app.enums.EtapaDespacho;
import com.genlogs.app.enums.TipoSstDespacho;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Despacho;
import com.genlogs.app.model.DespachoSst;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.DespachoRepository;
import com.genlogs.app.repository.DespachoSstRepository;
import com.genlogs.app.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DespachoSstService {

    private static final String ACTIVO = "A";

    private final DespachoSstRepository despachoSstRepository;
    private final DespachoRepository despachoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public BloqueoSstResponse estado(Despacho despacho, LocalDate hoy) {
        List<DespachoSst> filas = despachoSstRepository.findByDespacho_IdDespachoAndStatus(
                despacho.getIdDespacho(), ACTIVO);
        DespachoSst pase = deTipo(filas, TipoSstDespacho.PASE);
        DespachoSst sctr = deTipo(filas, TipoSstDespacho.SCTR);
        LocalDate vencimientoPase = pase == null ? null : pase.getFechaVencimiento();
        LocalDate vencimientoSctr = sctr == null ? null : sctr.getFechaVencimiento();
        String mensaje = DespachoBloqueoSst.mensaje(vencimientoPase, vencimientoSctr, hoy);
        EtapaDespacho siguiente = despacho.getEtapa().siguiente(despacho.getOrigen());
        return BloqueoSstResponse.builder()
                .bloqueaAvance(mensaje != null && DespachoBloqueoSst.exigeDocumento(siguiente))
                .mensaje(mensaje)
                .pase(documento(pase, hoy))
                .sctr(documento(sctr, hoy))
                .build();
    }

    /** Lo llama el avance, después de comprobar que la etapa es la que sigue. */
    public void exigirParaSalida(Despacho despacho, EtapaDespacho destino, LocalDate hoy) {
        if (!DespachoBloqueoSst.exigeDocumento(destino)) {
            return;
        }
        BloqueoSstResponse estado = estado(despacho, hoy);
        if (estado.getMensaje() != null) {
            throw new BusinessException(estado.getMensaje());
        }
    }

    @Transactional
    public void confirmar(Long idDespacho, ConfirmarSstRequest request) {
        if (request.getFechaVencimiento().isBefore(LocalDate.now(PlazoDespacho.LIMA))) {
            throw new BusinessException("La fecha de vencimiento no puede ser anterior a hoy.");
        }
        if (request.getFechaEmision() != null
                && request.getFechaEmision().isAfter(request.getFechaVencimiento())) {
            throw new BusinessException("La fecha de emisión no puede ser posterior a la de vencimiento.");
        }
        Despacho despacho = despachoRepository.findById(idDespacho)
                .orElseThrow(() -> new ResourceNotFoundException("Despacho no encontrado"));
        if (!ACTIVO.equals(despacho.getStatus())) {
            throw new BusinessException("No se puede confirmar el pase o el SCTR de un despacho anulado.");
        }
        if (despacho.getEtapa() == EtapaDespacho.ENTREGADO) {
            throw new BusinessException("No se puede confirmar el pase o el SCTR de un despacho ya entregado.");
        }

        Usuario usuario = usuarioActual();
        LocalDateTime ahora = LocalDateTime.now(PlazoDespacho.LIMA);
        despachoSstRepository
                .findByDespacho_IdDespachoAndTipoAndStatus(idDespacho, request.getTipo(), ACTIVO)
                .ifPresent(previa -> {
                    previa.setStatus("I");
                    previa.setUserUpdate(usuario.getNombreUsuario());
                    previa.setProcessUpdate("REEMPLAZO_SST");
                    previa.setDateUpdate(ahora);
                    despachoSstRepository.save(previa);
                    entityManager.flush();
                });

        DespachoSst nueva = new DespachoSst();
        nueva.setDespacho(despacho);
        nueva.setTipo(request.getTipo());
        nueva.setFechaEmision(request.getFechaEmision());
        nueva.setFechaVencimiento(request.getFechaVencimiento());
        nueva.setUrlDocumento(texto(request.getUrlDocumento()));
        nueva.setReferencia(texto(request.getReferencia()));
        nueva.setUsuario(usuario);
        nueva.setFechaConfirmacion(ahora);
        nueva.setUserCreate(usuario.getNombreUsuario());
        nueva.setProcessCreate("ALTA_SST");
        nueva.setDateCreate(ahora);
        nueva.setStatus(ACTIVO);
        despachoSstRepository.save(nueva);
    }

    private static DespachoSst deTipo(List<DespachoSst> filas, TipoSstDespacho tipo) {
        if (filas == null) {
            return null;
        }
        for (DespachoSst fila : filas) {
            if (fila.getTipo() == tipo) {
                return fila;
            }
        }
        return null;
    }

    private static DocumentoSstResponse documento(DespachoSst fila, LocalDate hoy) {
        if (fila == null) {
            return null;
        }
        return DocumentoSstResponse.builder()
                .vigente(!fila.getFechaVencimiento().isBefore(hoy))
                .vencimiento(fila.getFechaVencimiento())
                .referencia(fila.getReferencia())
                .build();
    }

    private static String texto(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private Usuario usuarioActual() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return usuarioRepository.findByNombreUsuario(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));
    }
}
