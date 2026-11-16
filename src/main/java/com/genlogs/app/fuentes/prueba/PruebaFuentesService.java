package com.genlogs.app.fuentes.prueba;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.EstadoFuente;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfig;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfigRepository;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.sourcing.ConsultaBusqueda;
import com.genlogs.app.sourcing.ConsultaFuente;
import com.genlogs.app.sourcing.ConsultaFuenteService;
import com.genlogs.app.sourcing.EstadoConsultaFuente;
import com.genlogs.app.sourcing.MetodoBusqueda;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * M2 (v3.3, BPMN 9-C) · Prueba de 10 pedidos por tienda.
 *
 * <ul>
 *   <li>{@link #buscar}: consulta las 5 tiendas con el 9-B.1 de Nicole y devuelve lo encontrado.
 *       No escribe en historial_sourcing.</li>
 *   <li>{@link #guardarCorrida}: guarda lo que marcó el Jefe (una fila por pedido y tienda en
 *       fuente_prueba) y deja los aciertos en fuente_busqueda.aciertos_prueba, que es lo que
 *       lee «Activar» (Luana, L5).</li>
 *   <li>Una tienda solo se activa con 4 o más aciertos de 10 (D-37). Si estaba activa y la
 *       nueva corrida no llega, se apaga.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PruebaFuentesService {

    static final int PEDIDOS = 10;
    /** D-37. Es el mismo mínimo que exige FuentesConfigService.activar. */
    static final int ACIERTOS_MINIMOS = 4;
    private static final String PROCESO = "PRUEBA_FUENTE";

    private final FuenteBusquedaConfigRepository fuenteRepository;
    private final FuentePruebaRepository pruebaRepository;
    private final ConsultaFuenteService consultas;
    private final UsuarioRepository usuarioRepository;

    /** Busca un pedido en las 5 tiendas. Una tienda que falla sale sola; las demás siguen. */
    public List<ConsultaFuente> buscar(BusquedaPruebaRequest req) {
        ConsultaBusqueda consulta = new ConsultaBusqueda(MetodoBusqueda.NUMERO_PARTE,
                req.numeroParte().trim(), limpio(req.marca()), null, null, null);
        List<ConsultaFuente> resultados = new ArrayList<>();
        for (FuenteBusquedaConfig fuente : enOrden(fuenteRepository.findAll())) {
            if (fuente.getEstado() != EstadoFuente.ACTIVA) {
                // Sin sesión probada (L1) no se consulta: no gasta cuota ni espera la pausa.
                resultados.add(ConsultaFuente.de(fuente.getCodigo().name(), EstadoConsultaFuente.OMITIDA_SESION,
                        fuente.getNombre() + " se omitió: primero prueba su sesión (hoy está "
                                + fuente.getEstado() + ").", List.of()));
                continue;
            }
            try {
                resultados.add(consultas.consultar(fuente, consulta));
            } catch (RuntimeException e) {
                log.warn("La prueba no pudo consultar {}: {}", fuente.getCodigo(), e.getMessage());
                resultados.add(ConsultaFuente.de(fuente.getCodigo().name(), EstadoConsultaFuente.CON_ERROR,
                        "No se pudo consultar " + fuente.getNombre() + ".", List.of()));
            }
        }
        return resultados;
    }

    /** Guarda una corrida por cada tienda que viene en la prueba. */
    @Transactional
    public List<CorridaPruebaResponse> guardarCorrida(CorridaPruebaRequest req) {
        Map<CodigoFuente, List<Marca>> porTienda = agrupar(req);
        String nombre = usuarioActual();
        Long idUsuario = idUsuario(nombre);
        LocalDateTime ahora = LocalDateTime.now();

        List<CorridaPruebaResponse> respuesta = new ArrayList<>();
        for (Map.Entry<CodigoFuente, List<Marca>> tienda : porTienda.entrySet()) {
            FuenteBusquedaConfig fuente = fuenteRepository.findByCodigo(tienda.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("No existe la fuente " + tienda.getKey()));
            int corrida = pruebaRepository.ultimaCorrida(fuente.getIdFuente()) + 1;
            int aciertos = 0;
            for (Marca m : tienda.getValue()) {
                FuentePrueba fila = new FuentePrueba();
                fila.setFuente(fuente);
                fila.setCorrida(corrida);
                fila.setPedido(m.pedido());
                fila.setAcerto(m.acerto());
                fila.setEnlace(m.enlace());
                fila.setProbadoPor(idUsuario);
                fila.setProbadoEn(ahora);
                fila.setUserCreate(nombre);
                fila.setProcessCreate(PROCESO);
                pruebaRepository.save(fila);
                if (m.acerto()) {
                    aciertos++;
                }
            }
            boolean aprobada = aciertos >= ACIERTOS_MINIMOS;
            boolean desactivada = !aprobada && Boolean.TRUE.equals(fuente.getActiva());
            fuente.setAciertosPrueba((short) aciertos);
            if (desactivada) {
                fuente.setActiva(false);
            }
            fuente.setEditadoPor(idUsuario);
            fuente.setUserUpdate(nombre);
            fuente.setProcessUpdate(PROCESO);
            fuente.setDateUpdate(ahora);
            fuenteRepository.save(fuente);
            respuesta.add(new CorridaPruebaResponse(fuente.getCodigo().name(), fuente.getNombre(), corrida,
                    aciertos, PEDIDOS, aprobada, desactivada));
        }
        return respuesta;
    }

    /** La última corrida de cada tienda, para la tabla de aciertos sobre 10. */
    @Transactional(readOnly = true)
    public List<ResumenPruebaResponse> resumen() {
        List<ResumenPruebaResponse> resumen = new ArrayList<>();
        for (FuenteBusquedaConfig f : enOrden(fuenteRepository.findAll())) {
            int corrida = pruebaRepository.ultimaCorrida(f.getIdFuente());
            List<ResumenPruebaResponse.Detalle> detalle = corrida == 0 ? List.of()
                    : pruebaRepository.findByFuente_IdFuenteAndCorridaAndStatusOrderByIdPrueba(
                                    f.getIdFuente(), corrida, "A").stream()
                            .map(p -> new ResumenPruebaResponse.Detalle(p.getPedido(),
                                    Boolean.TRUE.equals(p.getAcerto()), p.getEnlace(), p.getProbadoEn()))
                            .toList();
            Short aciertos = f.getAciertosPrueba();
            resumen.add(new ResumenPruebaResponse(f.getCodigo().name(), f.getNombre(),
                    Boolean.TRUE.equals(f.getActiva()), f.getEstado().name(), aciertos, corrida,
                    aciertos != null && aciertos >= ACIERTOS_MINIMOS, detalle));
        }
        return resumen;
    }

    // ---------------------------------------------------------------- apoyo

    private record Marca(String pedido, boolean acerto, String enlace) {
    }

    /**
     * Ordena lo marcado por tienda y valida: 10 pedidos distintos, cada tienda que aparece
     * está marcada en los 10 y una sola vez por pedido.
     */
    static Map<CodigoFuente, List<Marca>> agrupar(CorridaPruebaRequest req) {
        if (req == null || req.pedidos() == null || req.pedidos().size() != PEDIDOS) {
            throw new BusinessException("La prueba es de " + PEDIDOS + " pedidos");
        }
        Set<String> pedidosVistos = new HashSet<>();
        Map<CodigoFuente, List<Marca>> porTienda = new LinkedHashMap<>();
        for (CorridaPruebaRequest.PedidoPrueba p : req.pedidos()) {
            String pedido = p.pedido().replaceAll("\\s+", " ").trim();
            if (!pedidosVistos.add(pedido.toUpperCase(Locale.ROOT))) {
                throw new BusinessException("El pedido «" + pedido + "» está repetido: usa 10 pedidos distintos");
            }
            Set<CodigoFuente> tiendasDelPedido = new HashSet<>();
            for (CorridaPruebaRequest.ResultadoTienda r : p.resultados()) {
                CodigoFuente codigo = codigo(r.fuente());
                if (!tiendasDelPedido.add(codigo)) {
                    throw new BusinessException(codigo + " está marcada dos veces en «" + pedido + "»");
                }
                porTienda.computeIfAbsent(codigo, c -> new ArrayList<>())
                        .add(new Marca(pedido, r.acerto(), limpio(r.enlace())));
            }
        }
        for (Map.Entry<CodigoFuente, List<Marca>> tienda : porTienda.entrySet()) {
            if (tienda.getValue().size() != PEDIDOS) {
                throw new BusinessException(tienda.getKey() + " tiene " + tienda.getValue().size()
                        + " pedidos marcados; marca acertó o no acertó en los " + PEDIDOS);
            }
        }
        return porTienda;
    }

    static CodigoFuente codigo(String texto) {
        for (CodigoFuente c : CodigoFuente.values()) {
            if (c.name().equalsIgnoreCase(texto == null ? "" : texto.trim())) {
                return c;
            }
        }
        throw new ResourceNotFoundException("No existe la fuente " + texto);
    }

    private static List<FuenteBusquedaConfig> enOrden(List<FuenteBusquedaConfig> fuentes) {
        return fuentes.stream()
                .filter(f -> "A".equals(f.getStatus()))
                .sorted(Comparator.comparingInt(f -> f.getCodigo().ordinal()))
                .toList();
    }

    private static String limpio(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim();
        return t.isEmpty() ? null : t;
    }

    private static String usuarioActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SISTEMA";
    }

    private Long idUsuario(String nombre) {
        return usuarioRepository.findByNombreUsuario(nombre).map(u -> u.getIdUsuario()).orElse(null);
    }
}