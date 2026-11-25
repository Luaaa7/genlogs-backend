package com.genlogs.app.pagos;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.genlogs.app.dto.PagoIniciadoResponse;
import com.genlogs.app.dto.PortalComprobanteResponse;
import com.genlogs.app.dto.ResultadoPagoResponse;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.pagos.PagoEnLineaRegistro.Estado;
import com.genlogs.app.pagos.PagoEnLineaRegistro.Resultado;
import com.genlogs.app.repository.UsuarioRepository;
import com.genlogs.app.service.NotificacionService;

import lombok.RequiredArgsConstructor;

/**
 * Pago en línea del comprobante (BPMN 4.1). El cliente paga desde el portal con Mercado Pago;
 * el sistema consulta el pago a Mercado Pago (nunca se fía del aviso ni de la URL de regreso)
 * y, si está aprobado, registra el cobro con la misma regla de "Registrar pago".
 *
 * Se verifica por dos caminos: el regreso del cliente al portal y el aviso (webhook).
 * Con credenciales de prueba Mercado Pago no manda avisos, por eso el regreso también verifica.
 */
@Service
@RequiredArgsConstructor
public class PagoEnLineaService {

    private static final Logger log = LoggerFactory.getLogger(PagoEnLineaService.class);
    private static final String SIN_ACCESO = "El acceso del portal todavía no está habilitado";

    private final MercadoPagoCliente mercadoPago;
    private final PagoEnLineaRegistro registro;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

    @Value("${MERCADOPAGO_WEBHOOK_SECRET:}")
    private String secretoWebhook = "";

    /** Solo para pruebas. */
    void setSecretoWebhook(String secreto) {
        this.secretoWebhook = secreto;
    }

    public List<PortalComprobanteResponse> misComprobantes() {
        return registro.misComprobantes(idClienteActual(), mercadoPago.habilitado());
    }

    public PagoIniciadoResponse iniciar(Long idFacturacion) {
        Long idCliente = idClienteActual();
        if (!mercadoPago.habilitado()) {
            throw new BusinessException("El pago en línea todavía no está habilitado.");
        }
        PagoEnLineaRegistro.DatosPago datos = registro.datosParaPagar(idFacturacion, idCliente);
        MercadoPagoCliente.Preferencia preferencia = mercadoPago.crearPreferencia(new MercadoPagoCliente.PedidoPreferencia(
                PagoEnLineaRegistro.referencia(datos.idFacturacion()),
                "Comprobante " + datos.comprobante() + " - GENLOGS",
                datos.saldo(),
                PagoEnLineaRegistro.MONEDA));
        return new PagoIniciadoResponse(preferencia.urlPago());
    }

    /** El cliente volvió del checkout con el payment_id en la URL. */
    public ResultadoPagoResponse confirmarDesdePortal(String idPago) {
        Long idCliente = idClienteActual();
        MercadoPagoCliente.Pago pago = mercadoPago.consultarPago(idPago);
        return aRespuesta(procesar(pago, idCliente));
    }

    /**
     * Aviso de Mercado Pago. false si la firma no es válida (no se procesa).
     * Un aviso que no es de pagos se ignora y devuelve true para que no se reintente.
     */
    public boolean recibirAviso(String xSignature, String xRequestId, String dataId, String tipo) {
        if (tipo != null && !tipo.isBlank() && !"payment".equalsIgnoreCase(tipo)) {
            return true;
        }
        if (!FirmaMercadoPago.valida(xSignature, xRequestId, dataId, secretoWebhook)) {
            log.warn("Aviso de Mercado Pago con firma inválida o sin clave configurada; no se procesa");
            return false;
        }
        if (dataId == null || dataId.isBlank()) {
            return true;
        }
        procesar(mercadoPago.consultarPago(dataId), null);
        return true;
    }

    private Resultado procesar(MercadoPagoCliente.Pago pago, Long idCliente) {
        Resultado resultado;
        try {
            resultado = registro.registrar(pago, idCliente);
        } catch (DataIntegrityViolationException e) {
            // Dos verificaciones a la vez (regreso y aviso): la llave única ya lo guardó la otra.
            resultado = new Resultado(Estado.YA_REGISTRADO, null, null, pago.monto(), null, null);
        }
        avisar(resultado);
        return resultado;
    }

    private void avisar(Resultado r) {
        try {
            String enlace = "/facturacion";
            if (r.estado() == Estado.REGISTRADO) {
                String mensaje = "El cliente pagó S/ " + r.monto() + " del comprobante " + r.comprobante()
                        + " con Mercado Pago. El cobro ya está registrado.";
                notificacionService.avisarRol(RolSistema.JEFE_ADMINISTRATIVO, TipoAviso.PAGO_EN_LINEA,
                        "Pago en línea recibido", mensaje, enlace, false);
                notificacionService.avisarRol(RolSistema.CONTADOR_GENERAL, TipoAviso.PAGO_EN_LINEA,
                        "Pago en línea recibido", mensaje, enlace, false);
            } else if (r.estado() == Estado.REVISION) {
                notificacionService.avisarRol(RolSistema.JEFE_ADMINISTRATIVO, TipoAviso.PAGO_EN_LINEA,
                        "Pago en línea por revisar",
                        "Mercado Pago aprobó un pago del comprobante " + r.comprobante()
                                + " que no se registró solo: " + r.detalle() + ". Revísalo y regístralo a mano si corresponde.",
                        enlace, true);
            }
        } catch (RuntimeException e) {
            // El cobro ya quedó guardado: un aviso fallido no lo deshace.
            log.warn("No se pudo avisar el pago en línea: {}", e.getMessage());
        }
    }

    static ResultadoPagoResponse aRespuesta(Resultado r) {
        String mensaje = switch (r.estado()) {
            case REGISTRADO -> "¡Pago recibido! Registramos S/ " + r.monto() + " en el comprobante " + r.comprobante() + ".";
            case YA_REGISTRADO -> "Este pago ya estaba registrado. No se cobró dos veces.";
            case PENDIENTE -> "Tu pago está en proceso. Lo registraremos apenas Mercado Pago lo confirme.";
            case RECHAZADO -> "El pago no se completó. No se hizo ningún cobro; puedes intentarlo de nuevo.";
            case REVISION -> "Recibimos tu pago, pero necesita una revisión. GENLOGS se comunicará contigo.";
            case IGNORADO -> "No encontramos ese pago para tus comprobantes.";
        };
        return new ResultadoPagoResponse(r.estado().name(), mensaje, r.idFacturacion(), r.comprobante(),
                r.monto(), r.saldoPendiente());
    }

    private Long idClienteActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException(SIN_ACCESO);
        }
        Usuario usuario = usuarioRepository.findWithClienteByNombreUsuario(auth.getName()).orElse(null);
        if (usuario == null || usuario.getCliente() == null) {
            throw new AccessDeniedException(SIN_ACCESO);
        }
        return usuario.getCliente().getIdCliente();
    }
}