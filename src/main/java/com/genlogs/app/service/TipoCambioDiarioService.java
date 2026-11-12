package com.genlogs.app.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.TipoCambioDia;
import com.genlogs.app.enums.FuenteTipoCambio;
import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Moneda;
import com.genlogs.app.model.TipoCambio;
import com.genlogs.app.repository.MonedaRepository;
import com.genlogs.app.repository.TipoCambioRepository;
import com.genlogs.app.repository.UsuarioRepository;

/**
 * BE-D · Tipo de cambio del día (D-19, decisión 8.4).
 *
 * - Cada mañana (lun–sáb 8:05 y reintento a las 12:05, hora de Lima) consulta Factiliza y
 *   guarda el valor de venta como SUNAT.
 * - Si Factiliza falla, no guarda nada: TipoCambioService.delDia() sigue devolviendo el último
 *   valor (esDeHoy = false) y se avisa al Jefe Administrativo para que lo revise.
 * - El Jefe Administrativo puede corregir el del día (MANUAL); la consulta automática ya no lo pisa.
 */
@Service
public class TipoCambioDiarioService {

    private static final Logger log = LoggerFactory.getLogger(TipoCambioDiarioService.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final FactilizaTipoCambioClient factiliza;
    private final TipoCambioRepository tipoCambioRepository;
    private final MonedaRepository monedaRepository;
    private final UsuarioRepository usuarioRepository;
    private final TipoCambioService tipoCambioService;
    private final NotificacionService notificacionService;

    public TipoCambioDiarioService(FactilizaTipoCambioClient factiliza, TipoCambioRepository tipoCambioRepository,
            MonedaRepository monedaRepository, UsuarioRepository usuarioRepository,
            TipoCambioService tipoCambioService, NotificacionService notificacionService) {
        this.factiliza = factiliza;
        this.tipoCambioRepository = tipoCambioRepository;
        this.monedaRepository = monedaRepository;
        this.usuarioRepository = usuarioRepository;
        this.tipoCambioService = tipoCambioService;
        this.notificacionService = notificacionService;
    }

    @Scheduled(cron = "${genlogs.tipo-cambio.cron:0 5 8,12 * * MON-SAT}", zone = "America/Lima")
    public void tareaDiaria() {
        try {
            actualizarHoy();
        } catch (RuntimeException e) {
            log.error("Falló la tarea diaria del tipo de cambio", e);
        }
    }

    /** Trae el dólar de hoy si todavía no hay valor del día. No pisa una corrección manual. */
    @Transactional
    public void actualizarHoy() {
        LocalDate hoy = LocalDate.now(LIMA);
        if (tipoCambioRepository.findByFechaAndMoneda_CodigoMoneda(hoy, TipoCambioService.MONEDA_USD).isPresent()) {
            return;
        }
        Optional<FactilizaTipoCambioClient.TipoCambioDia> consulta = factiliza.consultar(hoy);
        if (consulta.isEmpty() || consulta.get().venta() == null || consulta.get().venta().signum() <= 0) {
            avisarFalla();
            return;
        }
        TipoCambio nuevo = new TipoCambio();
        nuevo.setFecha(hoy);
        nuevo.setMoneda(dolar());
        nuevo.setValor(consulta.get().venta());
        nuevo.setFuente(FuenteTipoCambio.SUNAT);
        tipoCambioRepository.save(nuevo);
        log.info("Tipo de cambio del {} guardado: {}", hoy, nuevo.getValor());
    }

    /** "Consultar ahora" del Jefe Administrativo: reintenta y devuelve el que queda vigente. */
    @Transactional
    public TipoCambioDia consultarAhora() {
        actualizarHoy();
        return tipoCambioService.delDia()
                .orElseThrow(() -> new BusinessException("Factiliza no respondió y no hay ningún tipo de cambio guardado. Escríbelo a mano."));
    }

    /** Corrección del Jefe Administrativo para el día de hoy (queda como MANUAL). */
    @Transactional
    public TipoCambioDia corregirHoy(BigDecimal valor) {
        LocalDate hoy = LocalDate.now(LIMA);
        TipoCambio tc = tipoCambioRepository.findByFechaAndMoneda_CodigoMoneda(hoy, TipoCambioService.MONEDA_USD)
                .orElseGet(() -> {
                    TipoCambio nuevo = new TipoCambio();
                    nuevo.setFecha(hoy);
                    nuevo.setMoneda(dolar());
                    return nuevo;
                });
        tc.setValor(valor);
        tc.setFuente(FuenteTipoCambio.MANUAL);
        tc.setEditadoPor(idUsuarioActual());
        tipoCambioRepository.save(tc);
        return new TipoCambioDia(hoy, TipoCambioService.MONEDA_USD, valor, FuenteTipoCambio.MANUAL, true);
    }

    private void avisarFalla() {
        String respaldo = tipoCambioService.delDia()
                .map(t -> "Mientras tanto se usa el último guardado: S/ " + t.valor().toPlainString() + " del " + t.fecha() + ".")
                .orElse("No hay ningún tipo de cambio guardado: escríbelo a mano.");
        log.warn("No se pudo traer el tipo de cambio de hoy desde Factiliza. {}", respaldo);
        notificacionService.avisarRol(RolSistema.JEFE_ADMINISTRATIVO, TipoAviso.TIPO_CAMBIO,
                "No se pudo traer el tipo de cambio de hoy",
                "Factiliza no respondió. " + respaldo + " Puedes corregirlo en Tipo de cambio.",
                "/parametros/tipo-cambio", false);
    }

    private Moneda dolar() {
        return monedaRepository.findByCodigoMoneda(TipoCambioService.MONEDA_USD)
                .orElseThrow(() -> new BusinessException("Falta la moneda USD en la tabla moneda"));
    }

    private Long idUsuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        return usuarioRepository.findByNombreUsuario(auth.getName()).map(u -> u.getIdUsuario()).orElse(null);
    }
}
