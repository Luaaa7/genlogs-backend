package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.Moneda;

@ExtendWith(MockitoExtension.class)
class AvisoAltoValorCotizacionTest {

    @Mock
    private ParametrosService parametrosService;
    @Mock
    private NotificacionService notificacionService;

    private AvisoAltoValorCotizacion aviso;

    @BeforeEach
    void setUp() {
        aviso = new AvisoAltoValorCotizacion(parametrosService, notificacionService);
    }

    @Test
    void veinticincoMilSolesAvisaYCincoMilNo() {
        when(parametrosService.getDecimal(ClavesParametro.MONTO_ALTO_VALOR_PEN))
                .thenReturn(new BigDecimal("20000"));

        Cotizacion alta = cotizacion("PEN", null);
        aviso.alAprobar(alta, new BigDecimal("25000"));
        assertTrue(alta.getAvisoAltoValorEnviado());

        Cotizacion baja = cotizacion("PEN", null);
        aviso.alAprobar(baja, new BigDecimal("5000"));
        assertFalse(Boolean.TRUE.equals(baja.getAvisoAltoValorEnviado()));

        verify(notificacionService, times(1)).avisarRol(
                eq(RolSistema.GERENTE_GENERAL), eq(TipoAviso.ALTO_VALOR),
                anyString(), anyString(), eq("/cotizaciones/15"), eq(true));
    }

    @Test
    void dolaresSeConviertenConElTipoDeCambioUsado() {
        when(parametrosService.getDecimal(ClavesParametro.MONTO_ALTO_VALOR_PEN))
                .thenReturn(new BigDecimal("20000"));
        Cotizacion cotizacion = cotizacion("USD", new BigDecimal("3.8000"));

        aviso.alAprobar(cotizacion, new BigDecimal("7000"));

        assertTrue(cotizacion.getAvisoAltoValorEnviado());
        verify(notificacionService).avisarRol(
                eq(RolSistema.GERENTE_GENERAL), eq(TipoAviso.ALTO_VALOR),
                anyString(), anyString(), anyString(), eq(true));
    }

    @Test
    void dolaresBajosNoAvisan() {
        when(parametrosService.getDecimal(ClavesParametro.MONTO_ALTO_VALOR_PEN))
                .thenReturn(new BigDecimal("20000"));
        Cotizacion cotizacion = cotizacion("USD", new BigDecimal("3.8000"));

        aviso.alAprobar(cotizacion, new BigDecimal("1000"));

        assertFalse(Boolean.TRUE.equals(cotizacion.getAvisoAltoValorEnviado()));
        verify(notificacionService, never()).avisarRol(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void elAvisoNoSeDuplica() {
        when(parametrosService.getDecimal(ClavesParametro.MONTO_ALTO_VALOR_PEN))
                .thenReturn(new BigDecimal("20000"));
        Cotizacion cotizacion = cotizacion("PEN", null);

        aviso.alAprobar(cotizacion, new BigDecimal("25000"));
        aviso.alAprobar(cotizacion, new BigDecimal("25000"));

        verify(notificacionService, times(1)).avisarRol(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void siFallaElParametroLaAprobacionSigue() {
        when(parametrosService.getDecimal(ClavesParametro.MONTO_ALTO_VALOR_PEN))
                .thenThrow(new BusinessException("falta"));
        Cotizacion cotizacion = cotizacion("PEN", null);

        aviso.alAprobar(cotizacion, new BigDecimal("25000"));

        assertFalse(Boolean.TRUE.equals(cotizacion.getAvisoAltoValorEnviado()));
    }

    private static Cotizacion cotizacion(String codigoMoneda, BigDecimal tipoCambio) {
        Moneda moneda = new Moneda();
        moneda.setCodigoMoneda(codigoMoneda);
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(15L);
        cotizacion.setCodigoCotizacion("COT-15");
        cotizacion.setMoneda(moneda);
        cotizacion.setTipoCambioUsado(tipoCambio);
        return cotizacion;
    }
}
