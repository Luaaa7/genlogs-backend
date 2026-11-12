package com.genlogs.app.sourcing;

import com.genlogs.app.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CostoPuestoLimaTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void importadoEnDolares_conviertePorTipoCambioYSumaGastos() {
        // 100 USD × 3.75 = 375 + flete 50 + seguro 10 + aduana 20 = 455.00
        assertThat(CostoPuestoLima.calcular(bd("100"), "USD", bd("3.75"), bd("50"), bd("10"), bd("20")))
                .isEqualByComparingTo("455.00");
    }

    @Test
    void enSoles_ignoraElTipoCambio() {
        assertThat(CostoPuestoLima.calcular(bd("250"), "PEN", null, bd("15"), null, null))
                .isEqualByComparingTo("265.00");
    }

    @Test
    void redondeaADosDecimales() {
        assertThat(CostoPuestoLima.calcular(bd("18.50"), "USD", bd("3.7512"), null, null, null))
                .isEqualByComparingTo("69.40");
    }

    @Test
    void enDolaresSinTipoCambio_esRechazado() {
        assertThatThrownBy(() -> CostoPuestoLima.calcular(bd("100"), "USD", null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tipo de cambio");
    }

    @Test
    void gastosNegativosOPrecioCero_sonRechazados() {
        assertThatThrownBy(() -> CostoPuestoLima.calcular(bd("100"), "PEN", null, bd("-1"), null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> CostoPuestoLima.calcular(bd("0"), "PEN", null, null, null, null))
                .isInstanceOf(BusinessException.class);
    }
}
