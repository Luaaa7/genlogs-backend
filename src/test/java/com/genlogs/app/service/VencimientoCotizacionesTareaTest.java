package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.genlogs.app.enums.RolSistema;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.EstadoCotizacion;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.EstadoCotizacionRepository;
import com.genlogs.app.repository.SeguimientoCotizacionRepository;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class VencimientoCotizacionesTareaTest {

    @Mock private VencimientoCotizacionesService servicio;
    private VencimientoCotizacionesTarea apagada;
    private VencimientoCotizacionesTarea encendida;

    @BeforeEach
    void setUp() {
        apagada = new VencimientoCotizacionesTarea(servicio, false);
        encendida = new VencimientoCotizacionesTarea(servicio, true);
    }

    @Test
    void laDiariaCorreYElArranqueNoHaceNadaSiEstaApagado() {
        apagada.diaria();
        apagada.alArrancar();
        verify(servicio, times(1)).vencer(LocalDate.now(PlazoDespacho.LIMA));
        verify(servicio, times(1)).avisarAdministracion(0);
    }

    @Test
    void alArrancarTrueEjecutaLaCorrida() {
        when(servicio.vencer(any())).thenReturn(2);
        encendida.alArrancar();
        verify(servicio).vencer(LocalDate.now(PlazoDespacho.LIMA));
        verify(servicio).avisarAdministracion(2);
    }

    @Test
    void unErrorNoTumbaNiLaDiariaNiElArranque() {
        when(servicio.vencer(any())).thenThrow(new RuntimeException("base caída"));
        assertDoesNotThrow(encendida::diaria);
        assertDoesNotThrow(encendida::alArrancar);
        verify(servicio, never()).avisarAdministracion(0);
    }

    @Test
    void siElAvisoFallaLaCorridaYaTermino() {
        when(servicio.vencer(any())).thenReturn(2);
        doThrow(new RuntimeException("campanita caída")).when(servicio).avisarAdministracion(2);
        assertDoesNotThrow(encendida::diaria);
        InOrder orden = inOrder(servicio);
        orden.verify(servicio).vencer(any());
        orden.verify(servicio).avisarAdministracion(2);
    }

    @Test
    void elValorPorDefectoEsSeguroYNoVenceSiNadieDefineLaVariable() throws Exception {
        Properties properties = new Properties();
        try (InputStream in = VencimientoCotizacionesTareaTest.class.getResourceAsStream("/application.properties")) {
            properties.load(in);
        }
        assertEquals("${VENCIMIENTO_ACTIVO:true}", properties.getProperty("genlogs.vencimiento.activo"));
        assertEquals("${VENCIMIENTO_AL_ARRANCAR:false}", properties.getProperty("genlogs.vencimiento.al-arrancar"));
        assertEquals("${VENCIMIENTO_SIMULACION:true}", properties.getProperty("genlogs.vencimiento.simulacion"),
                "sin la variable, la simulación queda activa y no vence nada");
        assertEquals("${VENCIMIENTO_MAXIMO_POR_CORRIDA:500}",
                properties.getProperty("genlogs.vencimiento.maximo-por-corrida"));
    }
}

@ExtendWith(MockitoExtension.class)
class VencimientoCotizacionesServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 4, 20);
    private static final String MENSAJE = "4 cotizaciones vencieron automáticamente";

    @Mock private CotizacionRepository cotizaciones;
    @Mock private EstadoCotizacionRepository estados;
    @Mock private SeguimientoCotizacionRepository seguimientos;
    @Mock private EntityManager entityManager;
    @Mock private NotificacionService notificaciones;

    @Test
    void siLaFilaYaNoEstaAbiertaNoLaVenceNiCreaSeguimiento() {
        EstadoCotizacion aprobada = new EstadoCotizacion();
        aprobada.setCodigoEstado("APROBADA");
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setFechaCotizacion(HOY.minusDays(16));
        cotizacion.setEstadoCotizacion(aprobada);
        when(cotizaciones.candidatasPorVencer(eq(HOY.minusDays(15)), eq(Set.of("ENVIADA", "EN_NEGOCIACION"))))
                .thenReturn(List.of(new CandidataVencimiento(1L, "COT-1", HOY.minusDays(16))));
        when(cotizaciones.bloquearPorId(1L)).thenReturn(Optional.of(cotizacion));
        when(estados.findByCodigoEstado("VENCIDA")).thenReturn(Optional.of(new EstadoCotizacion()));

        int vencidas = servicio(false, 500).vencer(HOY);

        assertEquals(0, vencidas);
        assertEquals("APROBADA", cotizacion.getEstadoCotizacion().getCodigoEstado());
        assertNull(cotizacion.getUserUpdate());
        verify(seguimientos, never()).save(any());
    }

    @Test
    void laSimulacionCuentaSinCambiarEstadoNiCrearSeguimientoNiAvisar() {
        when(cotizaciones.candidatasPorVencer(eq(HOY.minusDays(15)), eq(Set.of("ENVIADA", "EN_NEGOCIACION"))))
                .thenReturn(List.of(
                        new CandidataVencimiento(1L, "COT-1", HOY.minusDays(16)),
                        new CandidataVencimiento(2L, "COT-2", HOY.minusDays(20))));
        VencimientoCotizacionesService simulada = servicio(true, 500);

        assertEquals(2, simulada.vencer(HOY));
        simulada.avisarAdministracion(2);

        verify(cotizaciones, never()).bloquearPorId(anyLong());
        verify(seguimientos, never()).save(any());
        verify(notificaciones, never()).avisarRol(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void elTopeDe500DejaElRestoParaLaSiguienteCorrida() {
        List<CandidataVencimiento> todas = new ArrayList<>();
        List<Cotizacion> filas = new ArrayList<>();
        LocalDate fecha = HOY.minusDays(16);
        for (long id = 1; id <= 501; id++) {
            todas.add(new CandidataVencimiento(id, "COT-" + id, fecha));
            filas.add(abierta(id, fecha));
        }
        when(cotizaciones.candidatasPorVencer(eq(HOY.minusDays(15)), eq(Set.of("ENVIADA", "EN_NEGOCIACION"))))
                .thenReturn(todas)
                .thenReturn(List.of(todas.get(500)));
        when(cotizaciones.bloquearPorId(anyLong())).thenAnswer(invocacion -> {
            long id = invocacion.getArgument(0);
            return Optional.of(filas.get((int) id - 1));
        });
        EstadoCotizacion vencida = new EstadoCotizacion();
        vencida.setCodigoEstado("VENCIDA");
        when(estados.findByCodigoEstado("VENCIDA")).thenReturn(Optional.of(vencida));
        VencimientoCotizacionesService servicio = servicio(false, VencimientoCotizacionesService.MAXIMO_POR_DEFECTO);

        assertEquals(500, servicio.vencer(HOY));
        assertEquals("ENVIADA", filas.get(500).getEstadoCotizacion().getCodigoEstado());
        verify(cotizaciones, never()).bloquearPorId(501L);

        assertEquals(1, servicio.vencer(HOY));
        assertEquals("VENCIDA", filas.get(500).getEstadoCotizacion().getCodigoEstado());
        verify(seguimientos, times(501)).save(any());
    }

    @Test
    void avisaUnaVezALosDosRolesConElTotalYNoAvisaSiNoHuboNinguna() {
        VencimientoCotizacionesService servicio = servicio(false, 500);

        servicio.avisarAdministracion(4);
        servicio.avisarAdministracion(0);

        verify(notificaciones).avisarRol(RolSistema.ASISTENTE_LOGISTICO, TipoAviso.COTIZACION_VENCIDA,
                "Cotizaciones vencidas", MENSAJE, "/cotizaciones", false);
        verify(notificaciones).avisarRol(RolSistema.JEFE_ADMINISTRATIVO, TipoAviso.COTIZACION_VENCIDA,
                "Cotizaciones vencidas", MENSAJE, "/cotizaciones", false);
        verify(notificaciones, times(2)).avisarRol(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void siElAvisoFallaLaCotizacionSigueVencida() {
        Cotizacion cotizacion = abierta(1L, HOY.minusDays(16));
        when(cotizaciones.candidatasPorVencer(eq(HOY.minusDays(15)), eq(Set.of("ENVIADA", "EN_NEGOCIACION"))))
                .thenReturn(List.of(new CandidataVencimiento(1L, "COT-1", HOY.minusDays(16))));
        when(cotizaciones.bloquearPorId(1L)).thenReturn(Optional.of(cotizacion));
        EstadoCotizacion vencida = new EstadoCotizacion();
        vencida.setCodigoEstado("VENCIDA");
        when(estados.findByCodigoEstado("VENCIDA")).thenReturn(Optional.of(vencida));
        when(notificaciones.avisarRol(any(), any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("campanita caída"));
        VencimientoCotizacionesService servicio = servicio(false, 500);

        assertEquals(1, servicio.vencer(HOY));
        assertThrows(RuntimeException.class, () -> servicio.avisarAdministracion(1));

        assertEquals("VENCIDA", cotizacion.getEstadoCotizacion().getCodigoEstado());
        assertEquals(VencimientoCotizacionesService.USUARIO, cotizacion.getUserUpdate());
        verify(seguimientos, times(1)).save(any());
        verify(notificaciones, times(2)).avisarRol(any(), any(), any(), any(), any(), eq(false));
    }

    private Cotizacion abierta(long id, LocalDate fecha) {
        EstadoCotizacion enviada = new EstadoCotizacion();
        enviada.setCodigoEstado("ENVIADA");
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(id);
        cotizacion.setCodigoCotizacion("COT-" + id);
        cotizacion.setFechaCotizacion(fecha);
        cotizacion.setEstadoCotizacion(enviada);
        return cotizacion;
    }

    private VencimientoCotizacionesService servicio(boolean simulacion, int maximo) {
        return new VencimientoCotizacionesService(cotizaciones, estados, seguimientos, entityManager, notificaciones,
                simulacion, maximo);
    }
}
