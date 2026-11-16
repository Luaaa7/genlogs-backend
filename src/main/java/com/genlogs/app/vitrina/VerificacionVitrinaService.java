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
import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfig;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfigRepository;
import com.genlogs.app.sourcing.ConsultaBusqueda;
import com.genlogs.app.sourcing.ConsultaFuente;
import com.genlogs.app.sourcing.ConsultaFuenteService;
import com.genlogs.app.sourcing.EstadoConsultaFuente;
import com.genlogs.app.sourcing.HistorialSourcing;
import com.genlogs.app.sourcing.HistorialSourcingRepository;
import com.genlogs.app.sourcing.MetodoBusqueda;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RF-83 · Verificación de la vitrina. Un producto PUBLICADO se oculta si:
 *  1. falló en OCULTAR_FALLAS cotizaciones (2), o
 *  2. pasaron OCULTAR_SIN_VERIF_DIAS (60) sin verificarlo, o
 *  3. tocaba verificarlo (cada VERIFICACION_DIAS, 15) y la fuente ya no lo tiene.
 * R1 (v3.3, BPMN 10.1): la consulta va por el 9-B.1 ({@link ConsultaFuenteService}) sin guardar,
 * solo a la tienda del historial del producto y solo si está activa. Cada tienda tiene un cupo
 * por corrida de min(VERIFICACION_MAX_DIA, limite_diario / 5) consultas (aprobado por Luana);
 * las consultas cuentan en el mismo ControlCuota del 9-B.1, sin otro contador. Una tienda
 * inactiva, caída, bloqueada, sin sesión o sin cupo nunca oculta un producto: queda para otro
 * día, para «Verificar» del Asistente Logístico o para la regla de los 60 días.
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
    private final FuenteBusquedaConfigRepository fuenteRepository;
    private final ConsultaFuenteService consultaFuente;
    private Clock clock = Clock.systemDefaultZone();

    /** Si la fila VERIFICACION_MAX_DIA aún no está en parametro_sistema (v3_3.sql). */
    static final int MAX_DIA_POR_DEFECTO = 10;

    /** Estados en los que la tienda sí se consultó: gastan cupo aunque fallen. */
    private static final Set<EstadoConsultaFuente> LLEGO_A_LA_TIENDA = EnumSet.of(
            EstadoConsultaFuente.OK, EstadoConsultaFuente.SIN_RESULTADOS, EstadoConsultaFuente.BLOQUEADA,
            EstadoConsultaFuente.CON_ERROR, EstadoConsultaFuente.REQUIERE_VERIFICACION);

    public VerificacionVitrinaService(ProductoRepository productoRepository,
                                      HistorialSourcingRepository historialRepository,
                                      ParametrosService parametrosService,
                                      NotificacionService notificacionService,
                                      FuenteBusquedaConfigRepository fuenteRepository,
                                      ConsultaFuenteService consultaFuente) {
        this.productoRepository = productoRepository;
        this.historialRepository = historialRepository;
        this.parametrosService = parametrosService;
        this.notificacionService = notificacionService;
        this.fuenteRepository = fuenteRepository;
        this.consultaFuente = consultaFuente;
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
        Map<CodigoFuente, FuenteBusquedaConfig> activas = fuenteRepository.findByActivaTrueAndStatusOrderByIdFuente("A")
                .stream().collect(Collectors.toMap(FuenteBusquedaConfig::getCodigo, Function.identity(), (a, b) -> a));
        int maxDia = maxDia();
        Map<CodigoFuente, Integer> usadas = new EnumMap<>(CodigoFuente.class);

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
                switch (consultarFuente(p, activas, maxDia, usadas)) {
                    case DISPONIBLE -> {
                        p.setFechaUltimaVerificacion(ahora);
                        productoRepository.save(p);
                        verificados++;
                    }
                    case NO_DISPONIBLE -> {
                        ocultar(p, "la fuente ya no lo tiene");
                        ocultados++;
                    }
                    case SIN_CONSULTA -> { /* otro día, «Verificar» del asistente o se oculta a los 60 días */ }
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

    private Consulta consultarFuente(Producto p, Map<CodigoFuente, FuenteBusquedaConfig> activas,
                                     int maxDia, Map<CodigoFuente, Integer> usadas) {
        Optional<HistorialSourcing> ref = historialRepository.findFirstByIdProductoOrderByCreadoEnDesc(p.getIdProducto());
        String numeroParte = p.getNumeroParte() != null ? p.getNumeroParte() : ref.map(HistorialSourcing::getNumeroParte).orElse(null);
        CodigoFuente codigo = ref.map(VerificacionVitrinaService::codigoDe).orElse(null);
        FuenteBusquedaConfig fuente = codigo == null ? null : activas.get(codigo);
        if (fuente == null || numeroParte == null || numeroParte.isBlank()) return Consulta.SIN_CONSULTA;
        if (usadas.getOrDefault(codigo, 0) >= cupo(fuente, maxDia)) return Consulta.SIN_CONSULTA;

        ConsultaFuente r;
        try {
            r = consultaFuente.consultar(fuente, new ConsultaBusqueda(MetodoBusqueda.NUMERO_PARTE, numeroParte,
                    p.getMarca() != null ? p.getMarca().getNombreMarca() : null, null, null, null));
        } catch (RuntimeException e) {
            log.warn("No se pudo verificar {} en {}: {}", p.getCodigoProducto(), codigo, e.getMessage());
            return Consulta.SIN_CONSULTA;
        }
        if (r == null) return Consulta.SIN_CONSULTA;
        if (LLEGO_A_LA_TIENDA.contains(r.estado())) usadas.merge(codigo, 1, Integer::sum);
        return switch (r.estado()) {
            case OK -> r.fichas().isEmpty() ? Consulta.NO_DISPONIBLE : Consulta.DISPONIBLE;
            case SIN_RESULTADOS -> Consulta.NO_DISPONIBLE;
            // Caída, bloqueada, pide verificación, sin sesión, sin cuota o sin lector: no se oculta.
            default -> Consulta.SIN_CONSULTA;
        };
    }

    /** Cupo de la corrida para una tienda: min(VERIFICACION_MAX_DIA, limite_diario / 5). */
    static int cupo(FuenteBusquedaConfig fuente, int maxDia) {
        Integer limite = fuente.getLimiteDiario();
        int limiteDiario = limite == null || limite < 1 ? 1 : limite;
        return Math.max(0, Math.min(maxDia, limiteDiario / 5));
    }

    private int maxDia() {
        return parametrosService.buscar(ClavesParametro.VERIFICACION_MAX_DIA)
                .map(v -> {
                    try {
                        int n = Integer.parseInt(v);
                        return n >= 0 ? n : MAX_DIA_POR_DEFECTO;
                    } catch (NumberFormatException e) {
                        return MAX_DIA_POR_DEFECTO;
                    }
                })
                .orElse(MAX_DIA_POR_DEFECTO);
    }

    /**
     * Tienda del historial. AliExpress se guarda como ALIBABA con la consulta "ALIEXPRESS | ..."
     * (ConsultaFuenteService.codigoHistorial); PROVEEDOR o un código desconocido no se consultan.
     */
    static CodigoFuente codigoDe(HistorialSourcing ref) {
        String f = ref.getFuente();
        if (f == null || f.isBlank()) return null;
        if ("ALIBABA".equals(f) && ref.getConsulta() != null && ref.getConsulta().startsWith("ALIEXPRESS |")) {
            return CodigoFuente.ALIEXPRESS;
        }
        try {
            return CodigoFuente.valueOf(f.trim());
        } catch (IllegalArgumentException e) {
            return null;
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
