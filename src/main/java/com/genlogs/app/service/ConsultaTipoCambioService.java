package com.genlogs.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.TipoCambioDia;
import com.genlogs.app.enums.FuenteTipoCambio;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.TipoCambio;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.TipoCambioRepository;
import com.genlogs.app.repository.UsuarioRepository;

/**
 * Consulta diaria a Factiliza y corrección manual del Jefe (D-19, RF-86).
 * La lectura sigue en {@link TipoCambioService#delDia()}.
 */
@Service
public class ConsultaTipoCambioService {

    static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Logger log = LoggerFactory.getLogger(ConsultaTipoCambioService.class);
    private static final String TITULO = "No se pudo actualizar el tipo de cambio";
    private static final String MENSAJE = "La consulta de hoy falló. Sigue vigente el último tipo de cambio guardado.";

    private final ConsultaTipoCambio consultaTipoCambio;
    private final TipoCambioRepository tipoCambioRepository;
    private final MonedaRepository monedaRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;
    private final TipoCambioService tipoCambioService;

    public ConsultaTipoCambioService(ConsultaTipoCambio consultaTipoCambio,
            TipoCambioRepository tipoCambioRepository,
            MonedaRepository monedaRepository,
            UsuarioRepository usuarioRepository,
            NotificacionService notificacionService,
            TipoCambioService tipoCambioService) {
        this.consultaTipoCambio = consultaTipoCambio;
        this.tipoCambioRepository = tipoCambioRepository;
        this.monedaRepository = monedaRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionService = notificacionService;
        this.tipoCambioService = tipoCambioService;
    }

    /** La llama la tarea de las 08:00. Un fallo no borra filas: avisa y deja el último valor. */
    public void consultarHoy() {
        LocalDate hoy = LocalDate.now(LIMA);
        try {
            if (esManual(hoy)) {
                return;
            }
            BigDecimal venta = consultaTipoCambio.ventaDelDia(hoy).setScale(4, RoundingMode.HALF_UP);
            if (venta.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("El tipo de cambio no sirve");
            }
            guardar(hoy, venta, FuenteTipoCambio.SUNAT, null);
        } catch (RuntimeException e) {
            log.error("No se pudo consultar el tipo de cambio de {}", hoy, e);
            avisarFallo();
        }
    }

    private void avisarFallo() {
        try {
            notificacionService.avisarRol(RolSistema.JEFE_ADMINISTRATIVO, TipoAviso.TIPO_CAMBIO,
                    TITULO, MENSAJE, "/tipo-cambio", false);
        } catch (RuntimeException e) {
            log.error("No se pudo avisar que falló el tipo de cambio", e);
        }
    }

    /** Corrección del USD de hoy. Fuente MANUAL y editado_por el usuario de la sesión. */
    @Transactional
    public TipoCambioDia corregirHoy(BigDecimal valor) {
        BigDecimal venta = exigirValor(valor);
        LocalDate hoy = LocalDate.now(LIMA);
        guardar(hoy, venta, FuenteTipoCambio.MANUAL, usuarioActual().getIdUsuario());
        return tipoCambioService.delDia().orElseThrow(
                () -> new BusinessException("No se pudo leer el tipo de cambio que acaba de guardarse"));
    }

    private boolean esManual(LocalDate hoy) {
        return tipoCambioRepository.findByMoneda_CodigoMonedaAndFecha(TipoCambioService.MONEDA_USD, hoy)
                .map(fila -> fila.getFuente() == FuenteTipoCambio.MANUAL)
                .orElse(false);
    }

    private void guardar(LocalDate hoy, BigDecimal venta, FuenteTipoCambio fuente, Long editadoPor) {
        Moneda dolar = monedaRepository.findByCodigoMoneda(TipoCambioService.MONEDA_USD)
                .orElseThrow(() -> new BusinessException("No existe la moneda USD."));
        TipoCambio fila = tipoCambioRepository
                .findByMoneda_CodigoMonedaAndFecha(TipoCambioService.MONEDA_USD, hoy)
                .orElseGet(TipoCambio::new);
        fila.setFecha(hoy);
        fila.setMoneda(dolar);
        fila.setValor(venta);
        fila.setFuente(fuente);
        fila.setEditadoPor(editadoPor);
        tipoCambioRepository.save(fila);
    }

    private static BigDecimal exigirValor(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("El tipo de cambio tiene que ser mayor que 0");
        }
        int escala = Math.max(valor.stripTrailingZeros().scale(), 0);
        if (escala > 4) {
            throw new BusinessException("El tipo de cambio admite hasta 4 decimales");
        }
        return valor.setScale(4, RoundingMode.UNNECESSARY);
    }

    private Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
                || auth.getName() == null || auth.getName().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("No hay un usuario autenticado en la petición");
        }
        return usuarioRepository.findByNombreUsuario(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));
    }
}
