package com.genlogs.app.sourcing;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.SolicitudItem;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.SolicitudItemRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.sourcing.dto.GuardarUrlRequest;
import com.genlogs.app.sourcing.dto.ReferenciaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * RF-77 + RF-75 · Captura por URL: leer la página y guardar la referencia
 * confirmada por el asistente, con su costo puesto en Lima.
 */
@Service
@RequiredArgsConstructor
public class CapturaUrlService {

    private final LectorUrl lectorUrl;
    private final HistorialSourcingRepository historialRepository;
    private final SolicitudItemRepository solicitudItemRepository;
    private final MonedaRepository monedaRepository;
    private final UsuarioRepository usuarioRepository;

    /** Solo lee; no guarda nada. */
    public LectorUrl.DatosUrl leer(String url) {
        return lectorUrl.leer(url);
    }

    @Transactional
    public ReferenciaResponse guardar(GuardarUrlRequest req) {
        java.net.URI uri = LectorUrl.validarFormato(req.url());
        String codigoMoneda = req.codigoMoneda().trim().toUpperCase(Locale.ROOT);
        Moneda moneda = monedaRepository.findByCodigoMoneda(codigoMoneda)
                .orElseThrow(() -> new BusinessException("La moneda " + codigoMoneda + " no está registrada"));

        SolicitudItem item = null;
        if (req.idSolicitudItem() != null) {
            item = solicitudItemRepository.findById(req.idSolicitudItem())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No existe el ítem de solicitud " + req.idSolicitudItem()));
        }

        BigDecimal costo = CostoPuestoLima.calcular(req.precio(), codigoMoneda, req.tipoCambio(),
                req.flete(), req.seguro(), req.aduana());
        BigDecimal tcUsado = CostoPuestoLima.esSoles(codigoMoneda) ? null : req.tipoCambio();

        HistorialSourcing h = HistorialSourcing.builder()
                .solicitudItem(item)
                .fuente(LectorUrl.detectarFuente(uri.getHost()))
                .metodo(MetodoBusqueda.URL)
                .consulta(req.url().trim())
                .urlReferencia(req.url().trim())
                .urlImagenRef(vacioANull(req.urlImagen()))
                .titulo(req.titulo().trim())
                .marcaTexto(vacioANull(req.marca()))
                .numeroParte(vacioANull(req.numeroParte()))
                .precioRef(req.precio())
                .moneda(moneda)
                .tipoCambioUsado(tcUsado)
                .origen(req.origen())
                .costoPuestoLima(costo)
                .editadoAMano(req.editadoAMano())
                .estado(EstadoSourcing.CAPTURADO)
                .creadoPor(usuarioActual())
                .creadoEn(LocalDateTime.now())
                .build();
        return ReferenciaResponse.de(historialRepository.save(h));
    }

    private com.genlogs.app.model.Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return usuarioRepository.findByNombreUsuario(auth.getName()).orElse(null);
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
