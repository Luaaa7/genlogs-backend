package com.genlogs.app.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.DespachoResponse;
import com.genlogs.app.enums.EtapaDespacho;
import com.genlogs.app.enums.OrigenRepuesto;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.CotizacionDetalle;
import com.genlogs.app.model.Despacho;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.CotizacionDetalleRepository;
import com.genlogs.app.repository.DespachoRepository;
import com.genlogs.app.repository.OrdenCompraRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DespachoService {

    private static final Logger log = LoggerFactory.getLogger(DespachoService.class);
    private static final String ACTIVO = "A";

    private final DespachoRepository despachoRepository;
    private final OrdenCompraRepository ordenCompraRepository;
    private final CotizacionDetalleRepository cotizacionDetalleRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PlazoDespacho plazoDespacho;

    @Transactional(readOnly = true)
    public List<DespachoResponse> listar() {
        return despachoRepository.findByStatusOrderByIdDespachoDesc(ACTIVO).stream()
                .map(this::aRespuesta)
                .toList();
    }

    @Transactional(readOnly = true)
    public DespachoResponse buscar(Long id) {
        return aRespuesta(obtenerActivo(id));
    }

    @Transactional
    public DespachoResponse crear(Long idOrdenCompra, LocalDateTime inicio) {
        OrdenCompra orden = ordenCompraRepository.findById(idOrdenCompra)
                .orElseThrow(() -> new BusinessException("No existe la orden de compra"));
        if (!ACTIVO.equals(orden.getStatus())) {
            throw new BusinessException("La orden de compra no está activa");
        }
        String estado = orden.getCotizacion().getEstadoCotizacion() == null
                ? null
                : orden.getCotizacion().getEstadoCotizacion().getCodigoEstado();
        if (!"APROBADA".equals(estado)) {
            throw new BusinessException("La cotización de la orden no está aprobada");
        }
        if (despachoRepository.existsByOrdenCompra_IdOrdenCompraAndStatus(idOrdenCompra, ACTIVO)) {
            throw new BusinessException("Esta orden ya tiene un despacho activo");
        }

        List<CotizacionDetalle> lineas = cotizacionDetalleRepository.findByCotizacion(
                orden.getCotizacion().getIdCotizacion());
        OrigenLeido leido = origenDe(lineas, true);

        Usuario usuario = usuarioActual();
        PlazoDespacho.Rango plazo = plazoDespacho.calcular(leido.origen(), inicio);
        Despacho despacho = new Despacho();
        despacho.setOrdenCompra(orden);
        despacho.setCotizacion(orden.getCotizacion());
        despacho.setOrigen(leido.origen());
        despacho.setEtapa(EtapaDespacho.REGISTRADO);
        despacho.setPlazoDesde(plazo.desde());
        despacho.setPlazoHasta(plazo.hasta());
        despacho.setUsuario(usuario);
        despacho.setUserCreate(usuario.getNombreUsuario());
        despacho.setProcessCreate("ALTA_DESPACHO");
        despacho.setDateCreate(LocalDateTime.now(PlazoDespacho.LIMA));
        despacho.setStatus(ACTIVO);
        return aRespuesta(despachoRepository.save(despacho));
    }

    @Transactional
    public DespachoResponse avanzar(Long id, EtapaDespacho destino) {
        Despacho despacho = obtenerActivo(id);
        if (destino == EtapaDespacho.EN_ADUANA && despacho.getOrigen() == OrigenRepuesto.NACIONAL) {
            throw new BusinessException("Un despacho nacional no pasa por aduana.");
        }
        EtapaDespacho esperada = despacho.getEtapa().siguiente(despacho.getOrigen());
        if (esperada == null) {
            throw new BusinessException("El despacho ya está entregado.");
        }
        if (destino != esperada) {
            throw new BusinessException("La etapa solo avanza de " + despacho.getEtapa().etiqueta()
                    + " a " + esperada.etiqueta() + ".");
        }
        LocalDateTime ahora = LocalDateTime.now(PlazoDespacho.LIMA);
        switch (destino) {
            case EN_PREPARACION -> despacho.setFechaPreparacion(ahora);
            case EN_ADUANA -> despacho.setFechaAduana(ahora);
            case EN_TRANSITO -> despacho.setFechaTransito(ahora);
            case ENTREGADO -> despacho.setFechaEntrega(ahora);
            case REGISTRADO -> {
                /* no se vuelve a registrar */
            }
        }
        despacho.setEtapa(destino);
        Usuario usuario = usuarioActual();
        despacho.setUserUpdate(usuario.getNombreUsuario());
        despacho.setProcessUpdate("AVANCE_DESPACHO");
        despacho.setDateUpdate(ahora);
        return aRespuesta(despachoRepository.save(despacho));
    }

    private Despacho obtenerActivo(Long id) {
        Despacho despacho = despachoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Despacho no encontrado"));
        if (!ACTIVO.equals(despacho.getStatus())) {
            throw new ResourceNotFoundException("Despacho no encontrado");
        }
        return despacho;
    }

    private Usuario usuarioActual() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return usuarioRepository.findByNombreUsuario(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));
    }

    private DespachoResponse aRespuesta(Despacho despacho) {
        boolean incompleto = origenDe(cotizacionDetalleRepository.findByCotizacion(
                despacho.getCotizacion().getIdCotizacion()), false).incompleto();
        return DespachoResponse.builder()
                .idDespacho(despacho.getIdDespacho())
                .idOrdenCompra(despacho.getOrdenCompra().getIdOrdenCompra())
                .numeroOrdenCompra(despacho.getOrdenCompra().getNumeroOrdenCompra())
                .idCotizacion(despacho.getCotizacion().getIdCotizacion())
                .origen(despacho.getOrigen())
                .etapa(despacho.getEtapa())
                .etapaNombre(despacho.getEtapa().etiqueta())
                .fechaPreparacion(despacho.getFechaPreparacion())
                .fechaAduana(despacho.getFechaAduana())
                .fechaTransito(despacho.getFechaTransito())
                .fechaEntrega(despacho.getFechaEntrega())
                .plazoDesde(despacho.getPlazoDesde())
                .plazoHasta(despacho.getPlazoHasta())
                .idUsuario(despacho.getUsuario().getIdUsuario())
                .origenIncompleto(incompleto)
                .build();
    }

    private OrigenLeido origenDe(List<CotizacionDetalle> lineas, boolean avisar) {
        boolean incompleto = false;
        boolean importado = false;
        if (lineas != null) {
            for (CotizacionDetalle linea : lineas) {
                OrigenRepuesto efectivo = origenEfectivo(linea);
                if (efectivo == null) {
                    incompleto = true;
                    if (avisar) {
                        log.warn("Ítem {} sin origen en el detalle ni en el producto; se toma como NACIONAL",
                                linea.getIdCotizacionDetalle());
                    }
                } else if (efectivo == OrigenRepuesto.IMPORTADO) {
                    importado = true;
                }
            }
        }
        return new OrigenLeido(importado ? OrigenRepuesto.IMPORTADO : OrigenRepuesto.NACIONAL, incompleto);
    }

    /** El detalle manda. Si viene nulo, se lee producto.origen. Si tampoco, no se pudo determinar. */
    private OrigenRepuesto origenEfectivo(CotizacionDetalle linea) {
        if (linea.getOrigen() != null) {
            return linea.getOrigen();
        }
        Long idProducto = linea.getProducto() == null ? null : linea.getProducto().getIdProducto();
        if (idProducto == null) {
            return null;
        }
        List<String> leidos = productoRepository.findOrigenById(idProducto);
        if (leidos == null || leidos.isEmpty()) {
            return null;
        }
        return codigoOrigen(leidos.get(0));
    }

    private static OrigenRepuesto codigoOrigen(String crudo) {
        if (crudo == null || crudo.isBlank()) {
            return null;
        }
        try {
            return OrigenRepuesto.valueOf(crudo.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    record OrigenLeido(OrigenRepuesto origen, boolean incompleto) {
    }
}
