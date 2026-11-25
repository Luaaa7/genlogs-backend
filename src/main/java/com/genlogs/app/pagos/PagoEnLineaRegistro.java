package com.genlogs.app.pagos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.FacturacionResponse;
import com.genlogs.app.dto.PortalComprobanteResponse;
import com.genlogs.app.dto.RegistrarPagoRequest;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Facturacion;
import com.genlogs.app.model.PagoEnLinea;
import com.genlogs.app.repository.FacturacionRepository;
import com.genlogs.app.repository.PagoEnLineaRepository;
import com.genlogs.app.service.FacturacionService;
import com.genlogs.app.service.PlazoDespacho;

import lombok.RequiredArgsConstructor;

/**
 * Parte de base de datos del pago en línea (BPMN 4.1): qué comprobantes ve el cliente,
 * si uno se puede pagar y el registro del cobro. Las llamadas a Mercado Pago van fuera de
 * la transacción, en {@link PagoEnLineaService}.
 */
@Service
@RequiredArgsConstructor
public class PagoEnLineaRegistro {

    public static final String PROVEEDOR = "MERCADO_PAGO";
    static final String PREFIJO_REFERENCIA = "FAC-";
    static final String MONEDA = "PEN";
    static final String USUARIO_SISTEMA = "mercadopago";
    static final String PROCESO = "PAGO_EN_LINEA";
    static final String NO_ENCONTRADO = "Comprobante no encontrado";

    private final FacturacionRepository facturacionRepository;
    private final FacturacionService facturacionService;
    private final PagoEnLineaRepository pagoEnLineaRepository;

    /** Lo que se cobra en Mercado Pago. */
    public record DatosPago(Long idFacturacion, String comprobante, BigDecimal saldo) {
    }

    public enum Estado { REGISTRADO, YA_REGISTRADO, PENDIENTE, RECHAZADO, REVISION, IGNORADO }

    public record Resultado(Estado estado, Long idFacturacion, String comprobante, BigDecimal monto,
            BigDecimal saldoPendiente, String detalle) {
    }

    @Transactional(readOnly = true)
    public List<PortalComprobanteResponse> misComprobantes(Long idCliente, boolean pagoHabilitado) {
        return facturacionRepository.findByCliente(idCliente).stream()
                .map(f -> aRespuesta(f, pagoHabilitado))
                .toList();
    }

    /** Valida que el comprobante sea del cliente y se pueda pagar en línea; devuelve el saldo. */
    @Transactional(readOnly = true)
    public DatosPago datosParaPagar(Long idFacturacion, Long idCliente) {
        Facturacion f = delCliente(idFacturacion, idCliente);
        BigDecimal saldo = saldo(f);
        String motivo = motivoSinPago(f, saldo);
        if (motivo != null) {
            throw new BusinessException(motivo);
        }
        return new DatosPago(f.getIdFacturacion(), codigo(f), saldo);
    }

    /**
     * Registra el cobro si el pago está aprobado, en soles, no supera el saldo y no se registró
     * antes. idCliente null = aviso de Mercado Pago (sin sesión); con idCliente, el comprobante
     * tiene que ser de ese cliente.
     */
    @Transactional
    public Resultado registrar(MercadoPagoCliente.Pago pago, Long idCliente) {
        Long idFacturacion = idDeReferencia(pago.referenciaExterna());
        if (idFacturacion == null) {
            if (idCliente != null) {
                throw new ResourceNotFoundException(NO_ENCONTRADO);
            }
            return new Resultado(Estado.IGNORADO, null, null, pago.monto(), null, "Referencia ajena a GENLOGS");
        }
        Facturacion f = idCliente != null
                ? delCliente(idFacturacion, idCliente)
                : facturacionRepository.findById(idFacturacion).orElse(null);
        if (f == null) {
            return new Resultado(Estado.IGNORADO, idFacturacion, null, pago.monto(), null, "El comprobante no existe");
        }
        String codigo = codigo(f);
        BigDecimal saldo = saldo(f);

        if (pagoEnLineaRepository.existsByProveedorAndIdPagoExterno(PROVEEDOR, pago.id())) {
            return new Resultado(Estado.YA_REGISTRADO, f.getIdFacturacion(), codigo, pago.monto(), saldo, null);
        }
        if (!pago.aprobado()) {
            Estado estado = pago.pendiente() ? Estado.PENDIENTE : Estado.RECHAZADO;
            return new Resultado(estado, f.getIdFacturacion(), codigo, pago.monto(), saldo, pago.estado());
        }
        if (!MONEDA.equalsIgnoreCase(pago.moneda()) || !MONEDA.equalsIgnoreCase(monedaDe(f))) {
            return new Resultado(Estado.REVISION, f.getIdFacturacion(), codigo, pago.monto(), saldo,
                    "Moneda distinta: el pago llegó en " + pago.moneda() + " y el comprobante es en " + monedaDe(f));
        }
        if (pago.monto() == null || pago.monto().signum() <= 0 || pago.monto().compareTo(saldo) > 0) {
            return new Resultado(Estado.REVISION, f.getIdFacturacion(), codigo, pago.monto(), saldo,
                    "El pago (" + pago.monto() + ") supera el saldo pendiente (" + saldo + ")");
        }

        RegistrarPagoRequest pedido = new RegistrarPagoRequest();
        pedido.setMontoPagado(pago.monto().setScale(2, RoundingMode.HALF_UP));
        pedido.setFechaPago(pago.fechaAprobacion() != null ? pago.fechaAprobacion().toLocalDate() : LocalDate.now(PlazoDespacho.LIMA));
        pedido.setMedioPago("Mercado Pago");
        pedido.setNumeroOperacion(pago.id());
        FacturacionResponse actualizado = facturacionService.registrarPagoDelSistema(
                f.getIdFacturacion(), pedido, USUARIO_SISTEMA, PROCESO);

        PagoEnLinea registro = PagoEnLinea.builder()
                .facturacion(f)
                .proveedor(PROVEEDOR)
                .idPagoExterno(pago.id())
                .estado(pago.estado())
                .monto(pedido.getMontoPagado())
                .moneda(MONEDA)
                .fechaPago(pago.fechaAprobacion() != null ? pago.fechaAprobacion() : LocalDateTime.now(PlazoDespacho.LIMA))
                .build();
        registro.setUserCreate(USUARIO_SISTEMA);
        registro.setProcessCreate(PROCESO);
        pagoEnLineaRepository.saveAndFlush(registro);

        BigDecimal saldoNuevo = actualizado != null && actualizado.getSaldoPendiente() != null
                ? actualizado.getSaldoPendiente() : saldo.subtract(pedido.getMontoPagado());
        return new Resultado(Estado.REGISTRADO, f.getIdFacturacion(), codigo, pedido.getMontoPagado(), saldoNuevo, null);
    }

    // ------------------------------------------------------------------------

    private PortalComprobanteResponse aRespuesta(Facturacion f, boolean pagoHabilitado) {
        BigDecimal total = facturacionService.totalDe(f);
        BigDecimal pagado = f.getMontoPagado() != null ? f.getMontoPagado() : BigDecimal.ZERO;
        BigDecimal saldo = total.subtract(pagado);
        String motivo = pagoHabilitado ? motivoSinPago(f, saldo) : "El pago en línea todavía no está habilitado.";
        return PortalComprobanteResponse.builder()
                .id(f.getIdFacturacion())
                .comprobante(codigo(f))
                .tipo(f.getTipoComprobante() != null ? f.getTipoComprobante().getNombreTipo() : null)
                .fechaEmision(f.getFechaEmision())
                .fechaVencimiento(f.getFechaVencimiento())
                .total(total)
                .montoPagado(pagado)
                .saldoPendiente(saldo)
                .moneda(monedaDe(f))
                .estado(f.getEstadoFacturacion() != null ? f.getEstadoFacturacion().getNombreEstado() : null)
                .codigoEstado(codigoEstado(f))
                .numeroOcCliente(f.getOrdenCompra() != null ? vacioANull(f.getOrdenCompra().getNumeroOcCliente()) : null)
                .puedePagarEnLinea(motivo == null)
                .motivoSinPago(motivo)
                .build();
    }

    /** null si se puede pagar en línea; si no, el motivo en palabras del cliente. */
    static String motivoSinPago(Facturacion f, BigDecimal saldo) {
        String estado = codigoEstado(f);
        if ("ANULADA".equalsIgnoreCase(estado)) {
            return "El comprobante está anulado.";
        }
        if ("PAGADA".equalsIgnoreCase(estado) || saldo == null || saldo.signum() <= 0) {
            return "El comprobante ya está pagado.";
        }
        if (!MONEDA.equalsIgnoreCase(monedaDe(f))) {
            return "El pago en línea es solo para comprobantes en soles. Para dólares, paga por transferencia.";
        }
        return null;
    }

    /** Otra empresa o inactivo: el mismo 404, sin decir si existe. */
    private Facturacion delCliente(Long idFacturacion, Long idCliente) {
        Facturacion f = facturacionRepository.findById(idFacturacion)
                .orElseThrow(() -> new ResourceNotFoundException(NO_ENCONTRADO));
        Long dueno = f.getOrdenCompra() != null && f.getOrdenCompra().getCotizacion() != null
                && f.getOrdenCompra().getCotizacion().getCliente() != null
                ? f.getOrdenCompra().getCotizacion().getCliente().getIdCliente() : null;
        if (idCliente == null || !idCliente.equals(dueno) || !"A".equals(f.getStatus())) {
            throw new ResourceNotFoundException(NO_ENCONTRADO);
        }
        return f;
    }

    private BigDecimal saldo(Facturacion f) {
        BigDecimal pagado = f.getMontoPagado() != null ? f.getMontoPagado() : BigDecimal.ZERO;
        return facturacionService.totalDe(f).subtract(pagado);
    }

    static Long idDeReferencia(String referencia) {
        if (referencia == null || !referencia.startsWith(PREFIJO_REFERENCIA)) {
            return null;
        }
        try {
            return Long.valueOf(referencia.substring(PREFIJO_REFERENCIA.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String referencia(Long idFacturacion) {
        return PREFIJO_REFERENCIA + idFacturacion;
    }

    private static String codigo(Facturacion f) {
        return f.getSerieComprobante() + "-" + f.getNumeroComprobante();
    }

    private static String monedaDe(Facturacion f) {
        return f.getMoneda() != null ? f.getMoneda().getCodigoMoneda() : null;
    }

    private static String codigoEstado(Facturacion f) {
        return f.getEstadoFacturacion() != null ? f.getEstadoFacturacion().getCodigoEstado() : null;
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}