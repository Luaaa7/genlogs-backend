package com.genlogs.app.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.gestion.ConfirmarNumeroParteRequest;
import com.genlogs.app.dto.gestion.ItemIdentificacionResponse;
import com.genlogs.app.enums.EstadoSolicitud;
import com.genlogs.app.enums.TipoEventoSolicitud;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.EstadoNoPermitidoException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Marca;
import com.genlogs.app.model.SolicitudCotizacion;
import com.genlogs.app.model.SolicitudItem;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.MarcaRepository;
import com.genlogs.app.repository.SolicitudCotizacionRepository;
import com.genlogs.app.repository.UsuarioRepository;

/**
 * BE-B · Confirmación del N.º de parte y equivalentes (D-01). Antes de buscar, el asistente
 * completa o corrige marca y N.º de parte de cada repuesto y agrega equivalentes (sin repetir
 * ni copiar el principal). Cada cambio queda en la bitácora como EDICION.
 */
@Service
public class IdentificacionItemService {

    private static final Set<String> FINALES = Set.of(
            EstadoSolicitud.COTIZADA.name(), EstadoSolicitud.RECHAZADA.name(), EstadoSolicitud.CANCELADA.name());

    private final SolicitudCotizacionRepository solicitudRepository;
    private final MarcaRepository marcaRepository;
    private final UsuarioRepository usuarioRepository;
    private final BitacoraSolicitud bitacora;

    public IdentificacionItemService(SolicitudCotizacionRepository solicitudRepository, MarcaRepository marcaRepository,
            UsuarioRepository usuarioRepository, BitacoraSolicitud bitacora) {
        this.solicitudRepository = solicitudRepository;
        this.marcaRepository = marcaRepository;
        this.usuarioRepository = usuarioRepository;
        this.bitacora = bitacora;
    }

    @Transactional(readOnly = true)
    public List<ItemIdentificacionResponse> items(Long idSolicitud) {
        return solicitud(idSolicitud).getItems().stream()
                .filter(i -> i.getStatus() != null && i.getStatus().trim().equals("A"))
                .map(this::aRespuesta)
                .toList();
    }

    @Transactional
    public ItemIdentificacionResponse confirmar(Long idSolicitud, Long idItem, ConfirmarNumeroParteRequest r) {
        Usuario yo = usuarioActual();
        SolicitudCotizacion s = solicitud(idSolicitud);
        if (FINALES.contains(s.getEstadoSolicitud())) {
            throw new EstadoNoPermitidoException("La solicitud está " + s.getEstadoSolicitud().toLowerCase(Locale.ROOT)
                    + " y ya no se puede cambiar");
        }
        SolicitudItem item = s.getItems().stream()
                .filter(i -> i.getIdSolicitudItem().equals(idItem))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Repuesto no encontrado en esta solicitud"));

        String marcaTexto = vacio(r.marcaTexto()) ? null : r.marcaTexto().trim();
        if (r.idMarca() == null && marcaTexto == null) {
            throw new BusinessException("Indica la marca: con N.º de parte la marca es obligatoria");
        }
        if (r.idMarca() != null && !marcaRepository.existsById(r.idMarca())) {
            throw new BusinessException("La marca elegida no existe");
        }
        String numeroParte = r.numeroParte().trim();

        String antes = describir(item);
        item.setIdMarca(r.idMarca());
        item.setMarcaTexto(marcaTexto);
        item.setNumeroParte(numeroParte);
        item.setGtin(vacio(r.gtin()) ? null : r.gtin().trim());
        item.getEquivalentes().clear();
        item.getEquivalentes().addAll(equivalentesLimpios(r.equivalentes(), numeroParte));
        item.setParteConfirmada(true);
        item.setIdUsuarioConfirma(yo.getIdUsuario());
        item.setFechaConfirmacion(LocalDateTime.now());
        item.setUserUpdate(yo.getNombreUsuario());
        item.setProcessUpdate("SOLICITUD_CONFIRMAR_PARTE");
        item.setDateUpdate(LocalDateTime.now());
        solicitudRepository.save(s);

        bitacora.registrar(idSolicitud, yo.getIdUsuario(), TipoEventoSolicitud.EDICION,
                "Ítem " + idItem + ": " + antes + " → " + describir(item));
        return aRespuesta(item);
    }

    /** Sin repetidos (sin importar mayúsculas ni espacios) y sin copiar el N.º principal. */
    static List<String> equivalentesLimpios(List<String> equivalentes, String principal) {
        if (equivalentes == null) {
            return List.of();
        }
        Map<String, String> unicos = new LinkedHashMap<>();
        for (String e : equivalentes) {
            if (vacio(e)) {
                continue;
            }
            String limpio = e.trim();
            String clave = limpio.toUpperCase(Locale.ROOT);
            if (!clave.equals(principal.toUpperCase(Locale.ROOT))) {
                unicos.putIfAbsent(clave, limpio);
            }
        }
        return List.copyOf(unicos.values());
    }

    private ItemIdentificacionResponse aRespuesta(SolicitudItem i) {
        String confirmadoPor = i.getIdUsuarioConfirma() == null ? null
                : usuarioRepository.findById(i.getIdUsuarioConfirma()).map(Usuario::getNombreUsuario).orElse(null);
        return new ItemIdentificacionResponse(i.getIdSolicitudItem(), i.getDescripcion(), i.getCantidad(), i.getUnidad(),
                i.getIdMarca(), marca(i), i.getNumeroParte(), i.getGtin(), i.getUrlFoto(), i.getIdProducto(),
                i.getEquivalentes().stream().sorted().toList(), Boolean.TRUE.equals(i.getParteConfirmada()),
                confirmadoPor, i.getFechaConfirmacion());
    }

    private String marca(SolicitudItem i) {
        if (i.getIdMarca() != null) {
            return marcaRepository.findById(i.getIdMarca()).map(Marca::getNombreMarca).orElse(i.getMarcaTexto());
        }
        return i.getMarcaTexto();
    }

    private String describir(SolicitudItem i) {
        String m = marca(i);
        return "marca " + (m == null ? "—" : m) + ", N.º " + (i.getNumeroParte() == null ? "—" : i.getNumeroParte())
                + (i.getEquivalentes().isEmpty() ? "" : ", equivalentes " + String.join(" / ", i.getEquivalentes()));
    }

    private SolicitudCotizacion solicitud(Long id) {
        return solicitudRepository.findById(id)
                .filter(x -> x.getStatus() != null && x.getStatus().trim().equals("A"))
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada"));
    }

    private Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new AccessDeniedException("Sin sesión");
        }
        return usuarioRepository.findByNombreUsuario(auth.getName())
                .filter(u -> u.getCliente() == null)
                .orElseThrow(() -> new AccessDeniedException("Solo el personal interno confirma repuestos"));
    }

    private static boolean vacio(String v) {
        return v == null || v.isBlank();
    }
}
