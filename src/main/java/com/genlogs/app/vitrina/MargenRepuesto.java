package com.genlogs.app.vitrina;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.service.ClavesParametro;
import com.genlogs.app.service.ParametrosService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * D-17 · Margen de los repuestos de la vitrina: mismos rangos que D-12, con
 * parámetros propios MARGEN_REP_* (los carga BE-D). Mientras no existan, usa los
 * de servicios (MARGEN_SERV_*), que tienen los mismos valores de partida.
 *
 * Menos del mínimo o más del máximo → fuera de rango: el producto queda en
 * BORRADOR y solo el Jefe Administrativo lo publica (D-26). Entre el aviso y el
 * máximo se permite, con aviso.
 */
@Component
@RequiredArgsConstructor
public class MargenRepuesto {

    static final String MARGEN_REP_SUGERIDO = "MARGEN_REP_SUGERIDO";
    static final String MARGEN_REP_MIN = "MARGEN_REP_MIN";
    static final String MARGEN_REP_AVISO = "MARGEN_REP_AVISO";
    static final String MARGEN_REP_MAX = "MARGEN_REP_MAX";

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final ParametrosService parametrosService;

    public enum Evaluacion { NORMAL, CON_AVISO, FUERA_DE_RANGO }

    public record Rangos(BigDecimal sugerido, BigDecimal minimo, BigDecimal aviso, BigDecimal maximo) {
        public Evaluacion evaluar(BigDecimal margen) {
            if (margen.compareTo(minimo) < 0 || margen.compareTo(maximo) > 0) return Evaluacion.FUERA_DE_RANGO;
            if (margen.compareTo(aviso) > 0) return Evaluacion.CON_AVISO;
            return Evaluacion.NORMAL;
        }
    }

    public Rangos rangos() {
        return new Rangos(
                valor(MARGEN_REP_SUGERIDO, ClavesParametro.MARGEN_SERV_SUGERIDO),
                valor(MARGEN_REP_MIN, ClavesParametro.MARGEN_SERV_MIN),
                valor(MARGEN_REP_AVISO, ClavesParametro.MARGEN_SERV_AVISO),
                valor(MARGEN_REP_MAX, ClavesParametro.MARGEN_SERV_MAX));
    }

    /** precio = costo × (1 + margen / 100), a 2 decimales. */
    public static BigDecimal precio(BigDecimal costo, BigDecimal margen) {
        if (costo == null || costo.signum() <= 0) {
            throw new BusinessException("El costo puesto en Lima debe ser mayor a cero");
        }
        if (margen == null || margen.signum() < 0 || margen.compareTo(CIEN) > 0) {
            throw new BusinessException("El margen debe estar entre 0 y 100 %");
        }
        return costo.multiply(BigDecimal.ONE.add(margen.divide(CIEN, 6, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** margen = (precio / costo − 1) × 100, a 2 decimales. Null si falta algún dato. */
    public static BigDecimal margenDe(BigDecimal costo, BigDecimal precio) {
        if (costo == null || precio == null || costo.signum() <= 0) return null;
        return precio.divide(costo, 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE).multiply(CIEN)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal valor(String claveRepuesto, String claveServicios) {
        String texto = parametrosService.buscar(claveRepuesto).orElseGet(() -> parametrosService.get(claveServicios));
        try {
            return new BigDecimal(texto);
        } catch (NumberFormatException e) {
            throw new BusinessException("El parámetro " + claveRepuesto + " debe ser un número y vale \"" + texto + "\".");
        }
    }
}
