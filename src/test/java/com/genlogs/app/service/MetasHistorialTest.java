package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.genlogs.app.dto.MetaPeriodoResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.MetaPeriodo;
import com.genlogs.app.model.MetaPeriodoId;
import com.genlogs.app.repository.AccionCorrectivaRepository;
import com.genlogs.app.repository.MetaPeriodoRepository;
import com.genlogs.app.repository.SolicitudCotizacionRepository;
import com.genlogs.app.repository.UsuarioRepository;

/** Historial de resultados de metas (RF-72): varios meses seguidos, del más antiguo al más reciente. */
class MetasHistorialTest {

    private MetaPeriodoRepository metas;
    private MetasPeriodoService servicio;

    @BeforeEach
    void preparar() {
        metas = mock(MetaPeriodoRepository.class);
        SolicitudCotizacionRepository solicitudes = mock(SolicitudCotizacionRepository.class);
        when(metas.findById(any())).thenReturn(Optional.empty());
        when(solicitudes.plazosDelMes(any(), any())).thenReturn(List.of());
        servicio = new MetasPeriodoService(metas, mock(AccionCorrectivaRepository.class), solicitudes,
                mock(UsuarioRepository.class));
    }

    @Test
    void devuelveLosMesesPedidosCruzandoElAnio() {
        List<MetaPeriodoResponse> historial = servicio.historial(2026, 2, 4);

        assertEquals(4, historial.size());
        assertEquals(List.of("2025-11", "2025-12", "2026-1", "2026-2"),
                historial.stream().map(m -> m.anio() + "-" + m.mes()).toList());
    }

    @Test
    void cadaMesUsaSuMetaOElNoventaPorDefecto() {
        MetaPeriodo octubre = new MetaPeriodo();
        octubre.setAnio((short) 2026);
        octubre.setMes((short) 10);
        octubre.setMetaPorcentaje(new BigDecimal("85.00"));
        when(metas.findById(new MetaPeriodoId((short) 2026, (short) 10))).thenReturn(Optional.of(octubre));

        List<MetaPeriodoResponse> historial = servicio.historial(2026, 11, 2);

        assertEquals(0, historial.get(0).metaPorcentaje().compareTo(new BigDecimal("85.00")));
        assertEquals(0, historial.get(1).metaPorcentaje().compareTo(new BigDecimal("90.00")));
    }

    @Test
    void elNumeroDeMesesTieneLimites() {
        assertThrows(BusinessException.class, () -> servicio.historial(2026, 11, 0));
        assertThrows(BusinessException.class, () -> servicio.historial(2026, 11, 25));
        assertThrows(BusinessException.class, () -> servicio.historial(2026, 13, 6));
    }
}
