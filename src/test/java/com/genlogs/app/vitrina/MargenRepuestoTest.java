package com.genlogs.app.vitrina;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.service.ParametrosService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MargenRepuestoTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void precioEsCostoPorUnoMasMargen() {
        assertThat(MargenRepuesto.precio(bd("1000"), bd("25"))).isEqualByComparingTo("1250.00");
        assertThat(MargenRepuesto.precio(bd("455"), bd("25"))).isEqualByComparingTo("568.75");
    }

    @Test
    void margenDeUnPrecio() {
        assertThat(MargenRepuesto.margenDe(bd("1000"), bd("1250"))).isEqualByComparingTo("25.00");
        assertThat(MargenRepuesto.margenDe(null, bd("1250"))).isNull();
    }

    @Test
    void costoOMargenInvalidosSonRechazados() {
        assertThatThrownBy(() -> MargenRepuesto.precio(bd("0"), bd("25"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> MargenRepuesto.precio(bd("100"), bd("-1"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> MargenRepuesto.precio(bd("100"), bd("101"))).isInstanceOf(BusinessException.class);
    }

    @Test
    void usaMargenRepCuandoExisteYSiNoLosDeServicios() {
        ParametrosService parametros = mock(ParametrosService.class);
        when(parametros.buscar(anyString())).thenReturn(Optional.empty());
        when(parametros.buscar("MARGEN_REP_SUGERIDO")).thenReturn(Optional.of("30"));
        when(parametros.get("MARGEN_SERV_MIN")).thenReturn("15");
        when(parametros.get("MARGEN_SERV_AVISO")).thenReturn("35");
        when(parametros.get("MARGEN_SERV_MAX")).thenReturn("45");

        MargenRepuesto.Rangos r = new MargenRepuesto(parametros).rangos();

        assertThat(r.sugerido()).isEqualByComparingTo("30");
        assertThat(r.minimo()).isEqualByComparingTo("15");
        verify(parametros, never()).get("MARGEN_SERV_SUGERIDO");
    }

    @Test
    void evaluaLosRangosDeD12() {
        MargenRepuesto.Rangos r = new MargenRepuesto.Rangos(bd("25"), bd("15"), bd("35"), bd("45"));
        assertThat(r.evaluar(bd("14.99"))).isEqualTo(MargenRepuesto.Evaluacion.FUERA_DE_RANGO);
        assertThat(r.evaluar(bd("15"))).isEqualTo(MargenRepuesto.Evaluacion.NORMAL);
        assertThat(r.evaluar(bd("35"))).isEqualTo(MargenRepuesto.Evaluacion.NORMAL);
        assertThat(r.evaluar(bd("40"))).isEqualTo(MargenRepuesto.Evaluacion.CON_AVISO);
        assertThat(r.evaluar(bd("45"))).isEqualTo(MargenRepuesto.Evaluacion.CON_AVISO);
        assertThat(r.evaluar(bd("45.01"))).isEqualTo(MargenRepuesto.Evaluacion.FUERA_DE_RANGO);
    }
}
