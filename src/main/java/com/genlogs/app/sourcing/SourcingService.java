package com.genlogs.app.sourcing;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.SolicitudItem;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.SolicitudItemRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.sourcing.dto.BuscarReferenciasRequest;
import com.genlogs.app.sourcing.dto.ReferenciaResponse;
import com.genlogs.app.sourcing.dto.ResultadoBusquedaResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Subproceso 8.2 · Búsqueda en fuentes aprobadas (BPMN 9, RF-76).
 *
 * 1. Revisa el historial propio: si el mismo N.º de parte se buscó en las
 *    últimas 24 h, devuelve eso y NO consulta las fuentes.
 * 2. Si no, consulta todas las FuenteBusqueda que Spring encuentre (Mell las
 *    implementa en su paquete "fuentes"). Si no hay ninguna, la búsqueda
 *    funciona igual y sugiere la captura por URL.
 * 3. Guarda cada resultado como CAPTURADO en historial_sourcing, sin tocar el
 *    catálogo de productos.
 */
@Slf4j
@Service
public class SourcingService {

    static final long HORAS_REUTILIZACION = 24;

    private final HistorialSourcingRepository historialRepository;
    private final SolicitudItemRepository solicitudItemRepository;
    private final MonedaRepository monedaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ObjectProvider<FuenteBusqueda> fuentes;
    private Clock clock = Clock.systemDefaultZone();

    public SourcingService(HistorialSourcingRepository historialRepository,
                           SolicitudItemRepository solicitudItemRepository,
                           MonedaRepository monedaRepository,
                           UsuarioRepository usuarioRepository,
                           ObjectProvider<FuenteBusqueda> fuentes) {
        this.historialRepository = historialRepository;
        this.solicitudItemRepository = solicitudItemRepository;
        this.monedaRepository = monedaRepository;
        this.usuarioRepository = usuarioRepository;
        this.fuentes = fuentes;
    }

    /** Solo para pruebas: fija la hora "actual". */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Transactional
    public ResultadoBusquedaResponse buscar(BuscarReferenciasRequest req) {
        String numeroParte = normalizar(req.numeroParte());
        if (numeroParte == null) {
            throw new BusinessException("El N.º de parte es obligatorio");
        }

        SolicitudItem item = null;
        if (req.idSolicitudItem() != null) {
            item = solicitudItemRepository.findById(req.idSolicitudItem())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No existe el ítem de solicitud " + req.idSolicitudItem()));
        }

        // 1. Reutilizar búsquedas recientes (caso clave: no consultar la fuente dos veces en 24 h)
        if (!req.forzar()) {
            LocalDateTime desde = LocalDateTime.now(clock).minusHours(HORAS_REUTILIZACION);
            List<HistorialSourcing> recientes = historialRepository
                    .findByNumeroParteIgnoreCaseAndMetodoNotAndCreadoEnAfterOrderByCreadoEnDesc(
                            numeroParte, MetodoBusqueda.URL, desde);
            if (!recientes.isEmpty()) {
                List<ReferenciaResponse> refs = recientes.stream()
                        .filter(h -> h.getEstado() != EstadoSourcing.DESCARTADO)
                        .map(ReferenciaResponse::de)
                        .toList();
                return new ResultadoBusquedaResponse(true, List.of(), List.of(), refs.isEmpty(), refs);
            }
        }

        // 2. Consultar las fuentes que existan
        ConsultaBusqueda consulta = new ConsultaBusqueda(
                MetodoBusqueda.NUMERO_PARTE, numeroParte,
                normalizar(req.marca()), normalizar(req.gtin()), normalizar(req.palabras()), null);
        String textoConsulta = describir(consulta);
        Usuario usuario = usuarioActual();
        Map<String, Optional<Moneda>> monedas = new HashMap<>();

        List<String> consultadas = new ArrayList<>();
        List<String> conError = new ArrayList<>();
        List<HistorialSourcing> nuevos = new ArrayList<>();

        for (FuenteBusqueda fuente : fuentes.orderedStream().toList()) {
            String codigo = fuente.codigo();
            try {
                List<ResultadoBusqueda> resultados = fuente.buscar(consulta);
                consultadas.add(codigo);
                if (resultados == null) continue;
                for (ResultadoBusqueda r : resultados) {
                    nuevos.add(HistorialSourcing.builder()
                            .solicitudItem(item)
                            .fuente(codigo)
                            .metodo(MetodoBusqueda.NUMERO_PARTE)
                            .consulta(textoConsulta)
                            .titulo(r.titulo())
                            .marcaTexto(recortar(r.marca() != null ? r.marca() : consulta.marca(), 100))
                            .numeroParte(recortar(r.numeroParte() != null ? r.numeroParte() : numeroParte, 60))
                            .precioRef(r.precio())
                            .moneda(buscarMoneda(r.codigoMoneda(), monedas))
                            .urlReferencia(r.urlReferencia())
                            .urlImagenRef(r.urlImagen())
                            .estado(EstadoSourcing.CAPTURADO)
                            .creadoPor(usuario)
                            .creadoEn(LocalDateTime.now(clock))
                            .build());
                }
            } catch (RuntimeException e) {
                // Una fuente caída (cuota, red, llave vencida) no detiene la búsqueda.
                log.warn("La fuente {} falló al buscar {}: {}", codigo, numeroParte, e.getMessage());
                conError.add(codigo);
            }
        }

        List<ReferenciaResponse> refs = historialRepository.saveAll(nuevos).stream()
                .map(ReferenciaResponse::de)
                .toList();
        return new ResultadoBusquedaResponse(false, consultadas, conError, refs.isEmpty(), refs);
    }

    @Transactional(readOnly = true)
    public List<ReferenciaResponse> listar(Long idSolicitudItem, String numeroParte) {
        List<HistorialSourcing> lista;
        if (idSolicitudItem != null) {
            lista = historialRepository.findBySolicitudItem_IdSolicitudItemOrderByCreadoEnDesc(idSolicitudItem);
        } else if (normalizar(numeroParte) != null) {
            lista = historialRepository.findByNumeroParteIgnoreCaseOrderByCreadoEnDesc(normalizar(numeroParte));
        } else {
            throw new BusinessException("Indica el ítem de solicitud o el N.º de parte");
        }
        return lista.stream().map(ReferenciaResponse::de).toList();
    }

    /** Botón ÚTIL / DESCARTADO. Una referencia CONVERTIDA ya no cambia. */
    @Transactional
    public ReferenciaResponse cambiarEstado(Long idSourcing, EstadoSourcing nuevo) {
        if (nuevo == null || !nuevo.puedeMarcarseManualmente()) {
            throw new BusinessException("Solo se puede marcar como ÚTIL o DESCARTADO");
        }
        HistorialSourcing h = historialRepository.findById(idSourcing)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la referencia " + idSourcing));
        if (h.getEstado() == EstadoSourcing.CONVERTIDO) {
            throw new BusinessException("La referencia ya se convirtió en producto y no puede cambiar de estado");
        }
        h.setEstado(nuevo);
        return ReferenciaResponse.de(historialRepository.save(h));
    }

    // ---------- utilidades ----------

    private Moneda buscarMoneda(String codigo, Map<String, Optional<Moneda>> cache) {
        String c = normalizar(codigo);
        if (c == null) return null;
        return cache.computeIfAbsent(c.toUpperCase(Locale.ROOT), monedaRepository::findByCodigoMoneda)
                .orElse(null);
    }

    private Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return usuarioRepository.findByNombreUsuario(auth.getName()).orElse(null);
    }

    private static String describir(ConsultaBusqueda c) {
        StringJoiner j = new StringJoiner(" | ");
        j.add("P/N " + c.numeroParte());
        if (c.marca() != null) j.add("marca " + c.marca());
        if (c.gtin() != null) j.add("GTIN " + c.gtin());
        if (c.palabras() != null) j.add(c.palabras());
        return j.toString();
    }

    private static String normalizar(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
