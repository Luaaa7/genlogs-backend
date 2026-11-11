package com.genlogs.app.vitrina;

import com.genlogs.app.enums.EstadoPublicacion;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Producto;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.service.ClavesParametro;
import com.genlogs.app.service.NotificacionService;
import com.genlogs.app.service.ParametrosService;
import com.genlogs.app.sourcing.ConsultaBusqueda;
import com.genlogs.app.sourcing.FuenteBusqueda;
import com.genlogs.app.sourcing.HistorialSourcing;
import com.genlogs.app.sourcing.HistorialSourcingRepository;
import com.genlogs.app.sourcing.MetodoBusqueda;
import com.genlogs.app.sourcing.ResultadoBusqueda;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RF-83 · Verificación de la vitrina. Un producto PUBLICADO se oculta si:
 *  1. falló en OCULTAR_FALLAS cotizaciones (2), o
 *  2. pasaron OCULTAR_SIN_VERIF_DIAS (60) sin verificarlo, o
 *  3. tocaba verificarlo (cada VERIFICACION_DIAS, 15) y la fuente ya no lo tiene.
 * La consulta automática solo es posible con las fuentes conectadas (FuenteBusqueda);
 * lo capturado por URL lo verifica el Asistente Logístico con «Verificar».
 * Nada se borra (D-33): pasa a OCULTO y el asistente recibe un aviso.
 */
@Slf4j
@Service
public class VerificacionVitrinaService {

    public record Resumen(int revisados, int verificados, int ocultados) {
    }

    private final ProductoRepository productoRepository;
    private final HistorialSourcingRepository historialRepository;
    private final ParametrosService parametrosService;
    private final NotificacionService notificacionService;
    private final ObjectProvider<FuenteBusqueda> fuentes;
    private Clock clock = Clock.systemDefaultZone();

    public VerificacionVitrinaService(ProductoRepository productoRepository,
                                      HistorialSourcingRepository historialRepository,
                                      ParametrosService parametrosService,
                                      NotificacionService notificacionService,
                                      ObjectProvider<FuenteBusqueda> fuentes) {
        this.productoRepository = productoRepository;
        this.historialRepository = historialRepository;
        this.parametrosService = parametrosService;
        this.notificacionService = notificacionService;
        this.fuentes = fuentes;
    }

    /** Solo para pruebas. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** La corre la tarea programada una vez al día. */
    @Transactional
    public Resumen verificarPublicados() {
        int diasVerificacion = parametrosService.getEntero(ClavesParametro.VERIFICACION_DIAS);
        int diasSinVerificar = parametrosService.getEntero(ClavesParametro.OCULTAR_SIN_VERIF_DIAS);
        int fallasMaximas = parametrosService.getEntero(ClavesParametro.OCULTAR_FALLAS);
        LocalDateTime ahora = LocalDateTime.now(clock);

        Specification<Producto> publicados = (root, q, cb) -> cb.and(
                cb.equal(root.get("status"), "A"),
                cb.equal(root.get("estadoPublicacion"), EstadoPublicacion.PUBLICADO));
        List<Producto> productos = productoRepository.findAll(publicados, Sort.by("idProducto"));
        Map<String, FuenteBusqueda> conectadas = fuentes.orderedStream()
                .collect(Collectors.toMap(FuenteBusqueda::codigo, Function.identity(), (a, b) -> a));

        int verificados = 0;
        int ocultados = 0;
        for (Producto p : productos) {
            LocalDateTime ultima = p.getFechaUltimaVerificacion();
            if (p.getFallasCotizacion() != null && p.getFallasCotizacion() >= fallasMaximas) {
                ocultar(p, "falló en " + p.getFallasCotizacion() + " cotizaciones");
                ocultados++;
            } else if (ultima == null || ultima.isBefore(ahora.minusDays(diasSinVerificar))) {
                ocultar(p, diasSinVerificar + " días sin verificar");
                ocultados++;
            } else if (ultima.isBefore(ahora.minusDays(diasVerificacion))) {
                switch (consultarFuente(p, conectadas)) {
                    case DISPONIBLE -> {
                        p.setFechaUltimaVerificacion(ahora);
                        productoRepository.save(p);
                        verificados++;
                    }
                    case NO_DISPONIBLE -> {
                        ocultar(p, "la fuente ya no lo tiene");
                        ocultados++;
                    }
                    case SIN_CONSULTA -> { /* lo verifica el asistente; si no, se oculta a los 60 días */ }
                }
            }
        }
        if (ocultados > 0) {
            notificacionService.avisarRol(RolSistema.ASISTENTE_LOGISTICO, TipoAviso.GENERAL,
                    "Repuestos ocultados de la vitrina",
                    ocultados + (ocultados == 1 ? " repuesto se ocultó" : " repuestos se ocultaron")
                            + " en la verificación de hoy. Revísalos y vuelve a publicarlos si siguen disponibles.",
                    "/vitrina/productos?estado=OCULTO", false);
        }
        log.info("Verificación de la vitrina: {} revisados, {} verificados, {} ocultados",
                productos.size(), verificados, ocultados);
        return new Resumen(productos.size(), verificados, ocultados);
    }

    /** «Verificar»: el asistente confirmó hoy que el repuesto sigue disponible. */
    @Transactional
    public void marcarVerificado(Long idProducto) {
        Producto p = activo(idProducto);
        p.setFechaUltimaVerificacion(LocalDateTime.now(clock));
        productoRepository.save(p);
    }

    /** Una cotización no pudo conseguir el repuesto; al llegar a OCULTAR_FALLAS se oculta. */
    @Transactional
    public void registrarFallaCotizacion(Long idProducto) {
        Producto p = activo(idProducto);
        short fallas = (short) ((p.getFallasCotizacion() == null ? 0 : p.getFallasCotizacion()) + 1);
        p.setFallasCotizacion(fallas);
        if (p.getEstadoPublicacion() == EstadoPublicacion.PUBLICADO
                && fallas >= parametrosService.getEntero(ClavesParametro.OCULTAR_FALLAS)) {
            ocultar(p, "falló en " + fallas + " cotizaciones");
        } else {
            productoRepository.save(p);
        }
    }

    // ------------------------------------------------------------------

    enum Consulta { DISPONIBLE, NO_DISPONIBLE, SIN_CONSULTA }

    private Consulta consultarFuente(Producto p, Map<String, FuenteBusqueda> conectadas) {
        Optional<HistorialSourcing> ref = historialRepository.findFirstByIdProductoOrderByCreadoEnDesc(p.getIdProducto());
        String numeroParte = p.getNumeroParte() != null ? p.getNumeroParte() : ref.map(HistorialSourcing::getNumeroParte).orElse(null);
        FuenteBusqueda fuente = ref.map(HistorialSourcing::getFuente).map(conectadas::get).orElse(null);
        if (fuente == null || numeroParte == null || numeroParte.isBlank()) return Consulta.SIN_CONSULTA;
        try {
            List<ResultadoBusqueda> r = fuente.buscar(new ConsultaBusqueda(MetodoBusqueda.NUMERO_PARTE, numeroParte,
                    p.getMarca() != null ? p.getMarca().getNombreMarca() : null, null, null, null));
            return r == null || r.isEmpty() ? Consulta.NO_DISPONIBLE : Consulta.DISPONIBLE;
        } catch (RuntimeException e) {
            // Fuente caída o sin cuota: no se oculta por eso; se intenta otro día.
            log.warn("No se pudo verificar {} en {}: {}", p.getCodigoProducto(), fuente.codigo(), e.getMessage());
            return Consulta.SIN_CONSULTA;
        }
    }

    private void ocultar(Producto p, String motivo) {
        p.setEstadoPublicacion(EstadoPublicacion.OCULTO);
        p.setVisibleWeb(false);
        p.setUserUpdate("sistema");
        p.setProcessUpdate("VITRINA_VERIFICACION");
        productoRepository.save(p);
        log.info("Producto {} ocultado de la vitrina: {}", p.getCodigoProducto(), motivo);
    }

    private Producto activo(Long idProducto) {
        return productoRepository.findById(idProducto)
                .filter(p -> "A".equals(p.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("No existe el producto " + idProducto));
    }
}
