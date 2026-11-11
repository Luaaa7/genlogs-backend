package com.genlogs.app.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.TipoCambioDia;
import com.genlogs.app.repository.TipoCambioRepository;

/**
 * Lectura del tipo de cambio (Paso 0, solo lectura). Lo usan la cotizacion
 * (tipo_cambio_usado), la busqueda (costo puesto en Lima) y los paneles:
 *
 * <pre>
 *   tipoCambioService.delDia().map(TipoCambioDia::valor).orElse(null);
 * </pre>
 *
 * Si hoy todavia no hay valor, devuelve el ultimo guardado con esDeHoy = false
 * (D-19: si falla la consulta queda el ultimo). La consulta diaria a Factiliza y
 * la correccion manual del Jefe Administrativo son de BE-D (feat/be-d-tipo-cambio).
 */
@Service
@Transactional(readOnly = true)
public class TipoCambioService {

    public static final String MONEDA_USD = "USD";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final TipoCambioRepository tipoCambioRepository;
    private Clock clock = Clock.system(LIMA);

    public TipoCambioService(TipoCambioRepository tipoCambioRepository) {
        this.tipoCambioRepository = tipoCambioRepository;
    }

    /** Dolar del dia (o el ultimo guardado). Vacio si la tabla todavia no tiene ninguno. */
    public Optional<TipoCambioDia> delDia() {
        return delDia(MONEDA_USD);
    }

    /** Tipo de cambio del dia para una moneda (codigo ISO de 3 letras, como en moneda.codigo_moneda). */
    public Optional<TipoCambioDia> delDia(String codigoMoneda) {
        LocalDate hoy = LocalDate.now(clock);
        return tipoCambioRepository
                .findFirstByMoneda_CodigoMonedaAndFechaLessThanEqualOrderByFechaDesc(codigoMoneda, hoy)
                .map(t -> new TipoCambioDia(t.getFecha(), codigoMoneda, t.getValor(), t.getFuente(),
                        hoy.equals(t.getFecha())));
    }

    /** Solo para pruebas. */
    void setClock(Clock clock) {
        this.clock = clock;
    }
}