package com.genlogs.app.sourcing;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfig;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfigRepository;
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
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * RF-77 + RF-75 · Captura por URL.
 * Fuente activa con sesión y P2: lectura con sesión.
 * Sin sesión, P2 ausente o host que no es tienda activa: {@link LectorUrl} (5b), sin C2.
 * Bloqueo o error real de tienda: no se oculta con el fallback.
 */
@Service
@RequiredArgsConstructor
public class CapturaUrlService {

    private final LectorUrl lectorUrl;
    private final HistorialSourcingRepository historialRepository;
    private final SolicitudItemRepository solicitudItemRepository;
    private final MonedaRepository monedaRepository;
    private final UsuarioRepository usuarioRepository;
    private final FuenteBusquedaConfigRepository fuenteRepository;
    private final ConsultaFuenteService consultaFuente;

    public LectorUrl.DatosUrl leer(String url) {
        URI uri = UrlSegura.validarFormato(url);
        Optional<CodigoFuente> codigo = HostFuente.de(uri.getHost());
        if (codigo.isEmpty()) {
            return lectorUrl.leer(url);
        }
        Optional<FuenteBusquedaConfig> fuente = fuenteRepository.findByCodigo(codigo.get());
        if (fuente.isEmpty() || !Boolean.TRUE.equals(fuente.get().getActiva())) {
            return lectorUrl.leer(url);
        }

        ConsultaBusqueda consulta = new ConsultaBusqueda(
                MetodoBusqueda.URL, null, null, null, null, url);
        ConsultaFuente r = consultaFuente.consultarUrl(fuente.get(), url, consulta);
        return switch (r.estado()) {
            case OK -> {
                ResultadoBusqueda f = r.fichas().get(0);
                yield new LectorUrl.DatosUrl(url, ConsultaFuenteService.codigoHistorial(codigo.get()),
                        f.titulo(), f.precio(), f.codigoMoneda(), f.urlImagen(), false, null);
            }
            case BLOQUEADA -> new LectorUrl.DatosUrl(url, ConsultaFuenteService.codigoHistorial(codigo.get()),
                    null, null, null, null, true,
                    r.motivo() != null ? r.motivo() : "La tienda no permitió leer la página. Completa los datos a mano.");
            case CON_ERROR, REQUIERE_VERIFICACION -> new LectorUrl.DatosUrl(url,
                    ConsultaFuenteService.codigoHistorial(codigo.get()),
                    null, null, null, null, true,
                    r.motivo() != null ? r.motivo() : "No se pudo leer la página con la sesión de la tienda.");
            case OMITIDA_SESION, NO_CONECTADO, OMITIDA_CUOTA, SIN_RESULTADOS -> lectorUrl.leer(url);
        };
    }

    @Transactional
    public ReferenciaResponse guardar(GuardarUrlRequest req) {
        java.net.URI uri = UrlSegura.validarFormato(req.url());
        String urlImagen = UrlSegura.imagenOpcional(req.urlImagen());
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
                .urlImagenRef(urlImagen)
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
