package com.genlogs.app.sourcing;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfig;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfigRepository;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.SolicitudItem;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.SolicitudItemRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.ClavesParametro;
import com.genlogs.app.service.ParametrosService;
import com.genlogs.app.sourcing.dto.BuscarReferenciasRequest;
import com.genlogs.app.sourcing.dto.ReferenciaResponse;
import com.genlogs.app.sourcing.dto.ResultadoBusquedaResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.text.Normalizer;

/**
 * BPMN 9-B · Fuentes activas del 9-A vía 9-B.1. Si una tienda no se leyó por scraping
 * (inactiva, sin sesión, P2 no conectado), se usa el bean {@link FuenteBusqueda} (p. ej. eBay Browse)
 * sin duplicar una consulta que el 9-B.1 ya hizo a la tienda.
 */
@Slf4j
@Service
public class SourcingService {

    static final int DIAS_REUSO_POR_DEFECTO = 7;
    static final int LIMITE_BUSCADOR_DEFECTO = 20;
    static final int LIMITE_BUSCADOR_MAXIMO = 50;
    /** Acentos que se ignoran al buscar en el historial. */
    static final String ACENTOS = "áàäâãéèëêíìïîóòöôõúùüûñ";
    static final String SIN_ACENTOS = "aaaaaeeeeiiiiooooouuuun";

    private final HistorialSourcingRepository historialRepository;
    private final SolicitudItemRepository solicitudItemRepository;
    private final MonedaRepository monedaRepository;
    private final UsuarioRepository usuarioRepository;
    private final FuenteBusquedaConfigRepository fuenteRepository;
    private final ConsultaFuenteService consultaFuente;
    private final ParametrosService parametros;
    private final ObjectProvider<FuenteBusqueda> fuentesApi;
    private Clock clock = Clock.systemDefaultZone();

    public SourcingService(HistorialSourcingRepository historialRepository,
                           SolicitudItemRepository solicitudItemRepository,
                           MonedaRepository monedaRepository,
                           UsuarioRepository usuarioRepository,
                           FuenteBusquedaConfigRepository fuenteRepository,
                           ConsultaFuenteService consultaFuente,
                           ParametrosService parametros,
                           ObjectProvider<FuenteBusqueda> fuentesApi) {
        this.historialRepository = historialRepository;
        this.solicitudItemRepository = solicitudItemRepository;
        this.monedaRepository = monedaRepository;
        this.usuarioRepository = usuarioRepository;
        this.fuenteRepository = fuenteRepository;
        this.consultaFuente = consultaFuente;
        this.parametros = parametros;
        this.fuentesApi = fuentesApi;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

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

        if (!req.forzar()) {
            LocalDateTime desde = LocalDateTime.now(clock).minusDays(diasReuso());
            List<HistorialSourcing> recientes = historialRepository
                    .findByNumeroParteIgnoreCaseAndMetodoNotAndCreadoEnAfterOrderByCreadoEnDesc(
                            numeroParte, MetodoBusqueda.URL, desde);
            if (!recientes.isEmpty()) {
                List<ReferenciaResponse> refs = recientes.stream()
                        .filter(h -> h.getEstado() != EstadoSourcing.DESCARTADO)
                        .map(ReferenciaResponse::de)
                        .toList();
                return vacioReutilizado(refs);
            }
        }

        ConsultaBusqueda consulta = new ConsultaBusqueda(
                MetodoBusqueda.NUMERO_PARTE, numeroParte,
                normalizar(req.marca()), normalizar(req.gtin()), normalizar(req.palabras()), null);
        String textoConsulta = describir(consulta);
        Usuario usuario = usuarioActual();
        Map<String, Optional<Moneda>> monedas = new HashMap<>();

        List<String> consultadas = new ArrayList<>();
        List<String> conError = new ArrayList<>();
        List<String> omitidas = new ArrayList<>();
        List<String> bloqueadas = new ArrayList<>();
        List<String> sinResultados = new ArrayList<>();
        List<String> sinLector = new ArrayList<>();
        List<HistorialSourcing> nuevos = new ArrayList<>();
        List<String> descripciones = new ArrayList<>();
        Set<String> leidasEn9b1 = new HashSet<>();

        List<FuenteBusquedaConfig> fuentesActivas =
                fuenteRepository.findByActivaTrueAndStatusOrderByIdFuente("A");
        Set<String> codigosActivos = fuentesActivas.stream()
                .map(f -> f.getCodigo().name())
                .collect(java.util.stream.Collectors.toSet());
        List<FuenteBusqueda> apis = fuentesApi.orderedStream().toList();
        Set<String> codigosApi = apis.stream()
                .map(FuenteBusqueda::codigo)
                .collect(java.util.stream.Collectors.toSet());

        for (FuenteBusquedaConfig fuente : fuentesActivas) {
            if (fuente.getCodigo() == CodigoFuente.EBAY && codigosApi.contains("EBAY")) {
                continue;
            }
            ConsultaFuente r;
            try {
                r = consultaFuente.consultar(fuente, consulta);
            } catch (RuntimeException e) {
                log.warn("La fuente {} falló al buscar {}: {}", fuente.getCodigo(), numeroParte,
                        recortarLog(e.getMessage()));
                conError.add(fuente.getCodigo().name());
                continue;
            }
            switch (r.estado()) {
                case OMITIDA_CUOTA, OMITIDA_SESION -> omitidas.add(r.codigo());
                case NO_CONECTADO -> sinLector.add(r.codigo());
                case BLOQUEADA -> {
                    leidasEn9b1.add(r.codigo());
                    consultadas.add(r.codigo());
                    bloqueadas.add(r.codigo());
                }
                case CON_ERROR, REQUIERE_VERIFICACION -> {
                    leidasEn9b1.add(r.codigo());
                    consultadas.add(r.codigo());
                    conError.add(r.codigo());
                }
                case SIN_RESULTADOS -> {
                    leidasEn9b1.add(r.codigo());
                    consultadas.add(r.codigo());
                    sinResultados.add(r.codigo());
                }
                case OK -> {
                    leidasEn9b1.add(r.codigo());
                    consultadas.add(r.codigo());
                    String consultaGuardar = ConsultaFuenteService.consultaConFuente(
                            fuente.getCodigo(), textoConsulta);
                    for (ResultadoBusqueda ficha : r.fichas()) {
                        nuevos.add(aHistorial(item, ConsultaFuenteService.codigoHistorial(fuente.getCodigo()),
                                consultaGuardar, consulta, ficha, usuario, monedas));
                        descripciones.add(ficha.descripcion());
                    }
                }
            }
        }

        for (FuenteBusqueda api : apis) {
            String codigo = api.codigo();
            if ("EBAY".equals(codigo) && !codigosActivos.contains("EBAY")) {
                continue;
            }
            if (leidasEn9b1.contains(codigo) || consultadas.contains(codigo)) {
                continue;
            }
            try {
                List<ResultadoBusqueda> resultados = api.buscar(consulta);
                consultadas.add(codigo);
                if (resultados == null || resultados.isEmpty()) {
                    sinResultados.add(codigo);
                    continue;
                }
                omitidas.remove(codigo);
                sinLector.remove(codigo);
                for (ResultadoBusqueda ficha : NormalizadorFicha.sinRepetidos(resultados)) {
                    nuevos.add(aHistorial(item, codigo, textoConsulta, consulta, ficha, usuario, monedas));
                    descripciones.add(ficha.descripcion());
                }
            } catch (RuntimeException e) {
                log.warn("La fuente API {} falló al buscar {}: {}", codigo, numeroParte, recortarLog(e.getMessage()));
                conError.add(codigo);
            }
        }

        List<HistorialSourcing> guardados = historialRepository.saveAll(nuevos);
        List<ReferenciaResponse> refs = new ArrayList<>();
        for (int i = 0; i < guardados.size(); i++) {
            String desc = i < descripciones.size() ? descripciones.get(i) : null;
            refs.add(ReferenciaResponse.de(guardados.get(i), desc));
        }
        return new ResultadoBusquedaResponse(false, consultadas, conError, omitidas, bloqueadas,
                sinResultados, sinLector, refs.isEmpty(), refs);
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

    /**
     * Sin {@code q} se comporta como {@link #listar(Long, String)}.
     * Con {@code q} busca en título, N.º de parte y marca (sin mayúsculas ni acentos).
     */
    @Transactional(readOnly = true)
    public List<ReferenciaResponse> listar(Long idSolicitudItem, String numeroParte,
                                            String q, Boolean soloElegibles, Integer limite) {
        if (q == null) {
            return listar(idSolicitudItem, numeroParte);
        }
        int tope = limite == null ? LIMITE_BUSCADOR_DEFECTO : limite;
        if (tope < 1) {
            throw new BusinessException("El límite tiene que ser al menos 1");
        }
        if (tope > LIMITE_BUSCADOR_MAXIMO) {
            tope = LIMITE_BUSCADOR_MAXIMO;
        }
        return historialRepository.buscarPorTexto(
                        patronBusqueda(q), ACENTOS, SIN_ACENTOS, Boolean.TRUE.equals(soloElegibles),
                        PageRequest.of(0, tope))
                .stream()
                .map(ReferenciaResponse::de)
                .toList();
    }

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
        if (nuevo == EstadoSourcing.UTIL) {
            h.setMarcadaUtilDesde(OrigenMarcaUtil.BUSQUEDA);
            h.setMarcadaUtilPor(usuarioActual());
            h.setMarcadaUtilEn(LocalDateTime.now(clock));
        } else if (nuevo == EstadoSourcing.DESCARTADO) {
            h.setMarcadaUtilDesde(null);
            h.setMarcadaUtilPor(null);
            h.setMarcadaUtilEn(null);
        }
        return ReferenciaResponse.de(historialRepository.save(h));
    }

    /**
     * La cotización elige una referencia. Solo esa fila cambia (R1).
     * Una descartada no se puede elegir (R2). El origen queda en COTIZACION (R3).
     */
    @Transactional
    public ReferenciaResponse elegirDesdeCotizacion(Long idSourcing, Long idSolicitudItem) {
        HistorialSourcing h = historialRepository.findById(idSourcing)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la referencia " + idSourcing));
        if (h.getEstado() == EstadoSourcing.DESCARTADO) {
            throw new BusinessException("No se puede elegir una referencia descartada");
        }
        if (h.getEstado() == EstadoSourcing.CAPTURADO) {
            h.setEstado(EstadoSourcing.UTIL);
            h.setMarcadaUtilDesde(OrigenMarcaUtil.COTIZACION);
            h.setMarcadaUtilPor(usuarioActual());
            h.setMarcadaUtilEn(LocalDateTime.now(clock));
        }
        if (idSolicitudItem != null && h.getSolicitudItem() == null) {
            h.setSolicitudItem(solicitudItemRepository.findById(idSolicitudItem)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No existe el ítem de solicitud " + idSolicitudItem)));
        }
        return ReferenciaResponse.de(historialRepository.save(h));
    }

    private ResultadoBusquedaResponse vacioReutilizado(List<ReferenciaResponse> refs) {
        return new ResultadoBusquedaResponse(true, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), refs.isEmpty(), refs);
    }

    private int diasReuso() {
        return parametros.buscar(ClavesParametro.BUSQUEDA_REUSO_DIAS)
                .map(v -> {
                    try {
                        int n = Integer.parseInt(v);
                        return n > 0 ? n : DIAS_REUSO_POR_DEFECTO;
                    } catch (NumberFormatException e) {
                        return DIAS_REUSO_POR_DEFECTO;
                    }
                })
                .orElse(DIAS_REUSO_POR_DEFECTO);
    }

    private HistorialSourcing aHistorial(SolicitudItem item, String fuenteCodigo, String textoConsulta,
                                         ConsultaBusqueda consulta, ResultadoBusqueda r, Usuario usuario,
                                         Map<String, Optional<Moneda>> monedas) {
        return HistorialSourcing.builder()
                .solicitudItem(item)
                .fuente(fuenteCodigo)
                .metodo(MetodoBusqueda.NUMERO_PARTE)
                .consulta(textoConsulta)
                .titulo(r.titulo())
                .marcaTexto(recortar(r.marca() != null ? r.marca() : consulta.marca(), 100))
                .numeroParte(recortar(r.numeroParte() != null ? r.numeroParte() : consulta.numeroParte(), 60))
                .precioRef(r.precio())
                .moneda(buscarMoneda(r.codigoMoneda(), monedas))
                .urlReferencia(r.urlReferencia())
                .urlImagenRef(r.urlImagen())
                .estado(EstadoSourcing.CAPTURADO)
                .creadoPor(usuario)
                .creadoEn(LocalDateTime.now(clock))
                .build();
    }

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

    /** Mínimo 2 caracteres. El patrón ya no distingue mayúsculas ni acentos y no trae comodines libres. */
    static String patronBusqueda(String q) {
        String limpio = sinAcentos(q).trim().toLowerCase(Locale.ROOT);
        if (limpio.length() < 2) {
            throw new BusinessException("Escribe al menos 2 caracteres para buscar");
        }
        return "%" + limpio.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static String sinAcentos(String texto) {
        String normal = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return normal.replaceAll("\\p{M}", "");
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static String recortarLog(String s) {
        if (s == null) return "";
        return s.length() <= 200 ? s : s.substring(0, 200);
    }
}
