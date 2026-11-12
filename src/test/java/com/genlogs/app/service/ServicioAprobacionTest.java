package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.genlogs.app.enums.EstadoAprobacionServicio;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.CategoriaServicio;
import com.genlogs.app.model.Servicio;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.repository.CategoriaServicioRepository;
import com.genlogs.app.repository.ServicioRepository;
import com.genlogs.app.repository.UnidadMedidaRepository;

/**
 * RF-80, complemento de ServicioMargenTest: margen deducido del precio manual, rechazo
 * y bloqueo de la autoaprobación. Sin base de datos.
 */
class ServicioAprobacionTest {

    private ServicioRepository servicios;
    private ServicioService servicio;

    @BeforeEach
    void preparar() {
        servicios = mock(ServicioRepository.class);
        CategoriaServicioRepository categorias = mock(CategoriaServicioRepository.class);
        UnidadMedidaRepository unidades = mock(UnidadMedidaRepository.class);
        ParametrosService parametros = mock(ParametrosService.class);
        when(parametros.getDecimal(ClavesParametro.MARGEN_SERV_MIN)).thenReturn(new BigDecimal("15"));
        when(parametros.getDecimal(ClavesParametro.MARGEN_SERV_MAX)).thenReturn(new BigDecimal("45"));
        when(servicios.existsByCodigoServicioIgnoreCase(any())).thenReturn(false);
        when(servicios.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        CategoriaServicio categoria = new CategoriaServicio();
        categoria.setIdCategoriaServicio(1);
        when(categorias.findById(1)).thenReturn(Optional.of(categoria));
        UnidadMedida unidad = new UnidadMedida();
        unidad.setIdUnidadMedida(1);
        when(unidades.findById(1)).thenReturn(Optional.of(unidad));
        servicio = new ServicioService(servicios, categorias, unidades, parametros);
        comoUsuario("jefe");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    // ---- Precio manual: el margen sale del precio ----

    @Test
    void elPrecioManualDefineElMargenAunqueSeEnvieOtro() {
        // Se envía margen 30 (en rango), pero 150 sobre costo 100 es 50 %: queda pendiente.
        Servicio guardado = servicio.registrar(datos("100", "30", true, "150"));

        assertEquals(0, guardado.getMargen().compareTo(new BigDecimal("50.00")));
        assertEquals(0, guardado.getPrecioReferencial().compareTo(new BigDecimal("150")));
        assertEquals(EstadoAprobacionServicio.PENDIENTE_APROBACION, guardado.getEstadoAprobacion());
    }

    @Test
    void unPrecioManualDentroDelRangoQuedaAprobado() {
        Servicio guardado = servicio.registrar(datos("1000", "99", true, "1250"));

        assertEquals(0, guardado.getMargen().compareTo(new BigDecimal("25.00")));
        assertEquals(EstadoAprobacionServicio.APROBADO, guardado.getEstadoAprobacion());
    }

    @Test
    void elMargenDelPrecioManualSeRedondeaADosDecimales() {
        Servicio guardado = servicio.registrar(datos("300", null, true, "400"));

        assertEquals(0, guardado.getMargen().compareTo(new BigDecimal("33.33")));
    }

    @Test
    void unPrecioManualMenorQueElCostoSeRechaza() {
        assertThrows(BusinessException.class, () -> servicio.registrar(datos("100", null, true, "90")));
        verify(servicios, never()).save(any());
    }

    @Test
    void unPrecioManualQueSuperaElCienPorCientoSeRechaza() {
        assertThrows(BusinessException.class, () -> servicio.registrar(datos("100", null, true, "200.01")));
        verify(servicios, never()).save(any());
    }

    @Test
    void elPrecioManualExigeCostoYPrecio() {
        assertThrows(BusinessException.class, () -> servicio.registrar(datos(null, null, true, "150")));
        assertThrows(BusinessException.class, () -> servicio.registrar(datos("100", null, true, null)));
        assertThrows(BusinessException.class, () -> servicio.registrar(datos("100", null, true, "0")));
        verify(servicios, never()).save(any());
    }

    @Test
    void elPrecioCalculadoSigueLaFormula() {
        Servicio guardado = servicio.registrar(datos("1000", "25", false, null));

        assertEquals(0, guardado.getPrecioReferencial().compareTo(new BigDecimal("1250.00")));
        assertEquals(EstadoAprobacionServicio.APROBADO, guardado.getEstadoAprobacion());
    }

    // ---- Rechazo ----

    @Test
    void otroJefeRechazaUnPendiente() {
        Servicio pendiente = pendienteDe("asistente", null);
        when(servicios.findById(7L)).thenReturn(Optional.of(pendiente));

        Servicio rechazado = servicio.rechazar(7L);

        assertEquals(EstadoAprobacionServicio.RECHAZADO, rechazado.getEstadoAprobacion());
        assertEquals("jefe", rechazado.getUserUpdate());
        assertEquals("RECHAZO_SERVICIO", rechazado.getProcessUpdate());
    }

    @Test
    void noSeRechazaNiApruebaLoQueNoEstaPendiente() {
        Servicio aprobado = pendienteDe("asistente", null);
        aprobado.setEstadoAprobacion(EstadoAprobacionServicio.APROBADO);
        when(servicios.findById(7L)).thenReturn(Optional.of(aprobado));

        assertThrows(BusinessException.class, () -> servicio.rechazar(7L));
        assertThrows(BusinessException.class, () -> servicio.aprobar(7L));
        verify(servicios, never()).save(any());
    }

    @Test
    void alEditarUnRechazadoSeVuelveACalcularElEstado() {
        Servicio rechazado = pendienteDe("asistente", null);
        rechazado.setEstadoAprobacion(EstadoAprobacionServicio.RECHAZADO);
        when(servicios.findById(7L)).thenReturn(Optional.of(rechazado));

        Servicio editado = servicio.actualizar(7L, datos("100", "20", false, null));

        assertEquals(EstadoAprobacionServicio.APROBADO, editado.getEstadoAprobacion());
        assertEquals(0, editado.getPrecioReferencial().compareTo(new BigDecimal("120.00")));
    }

    // ---- Nadie aprueba ni rechaza lo suyo ----

    @Test
    void elJefeNoApruebaLoQueRegistro() {
        when(servicios.findById(7L)).thenReturn(Optional.of(pendienteDe("jefe", null)));

        assertThrows(AccessDeniedException.class, () -> servicio.aprobar(7L));
        verify(servicios, never()).save(any());
    }

    @Test
    void elJefeNoRechazaLoQueEditoAunqueLoHayaRegistradoOtro() {
        when(servicios.findById(7L)).thenReturn(Optional.of(pendienteDe("asistente", "JEFE")));

        assertThrows(AccessDeniedException.class, () -> servicio.rechazar(7L));
        verify(servicios, never()).save(any());
    }

    @Test
    void otroJefeSiApruebaLoQueRegistroElPrimero() {
        when(servicios.findById(7L)).thenReturn(Optional.of(pendienteDe("jefe", null)));
        comoUsuario("jefe2");

        Servicio aprobado = servicio.aprobar(7L);

        assertEquals(EstadoAprobacionServicio.APROBADO, aprobado.getEstadoAprobacion());
        assertEquals("jefe2", aprobado.getUserUpdate());
    }

    @Test
    void elSolicitanteEsElUltimoQueEditoOQuienRegistro() {
        assertEquals("ana", ServicioService.solicitanteDe(pendienteDe("ana", null)));
        assertEquals("luis", ServicioService.solicitanteDe(pendienteDe("ana", "luis")));
        assertNull(ServicioService.solicitanteDe(pendienteDe(null, " ")));
    }

    @Test
    void elRegistroGuardaAQuienLoSolicita() {
        comoUsuario("asistente");
        Servicio guardado = servicio.registrar(datos("100", "50", false, null));

        assertEquals(EstadoAprobacionServicio.PENDIENTE_APROBACION, guardado.getEstadoAprobacion());
        assertTrue("asistente".equals(ServicioService.solicitanteDe(guardado)));
    }

    // ---- Apoyo ----

    private static void comoUsuario(String nombre) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(nombre, "x", List.of()));
    }

    private static Servicio pendienteDe(String registro, String edicion) {
        Servicio servicio = datos("100", "50", false, null);
        servicio.setIdServicio(7L);
        servicio.setEstadoAprobacion(EstadoAprobacionServicio.PENDIENTE_APROBACION);
        servicio.setUserCreate(registro);
        servicio.setUserUpdate(edicion);
        return servicio;
    }

    private static Servicio datos(String costo, String margen, Boolean manual, String precio) {
        CategoriaServicio categoria = new CategoriaServicio();
        categoria.setIdCategoriaServicio(1);
        UnidadMedida unidad = new UnidadMedida();
        unidad.setIdUnidadMedida(1);
        Servicio servicio = new Servicio();
        servicio.setCodigoServicio("SRV-1");
        servicio.setNombreServicio("Mantenimiento");
        servicio.setCategoriaServicio(categoria);
        servicio.setUnidadMedida(unidad);
        servicio.setCosto(costo == null ? null : new BigDecimal(costo));
        servicio.setMargen(margen == null ? null : new BigDecimal(margen));
        servicio.setPrecioManual(manual);
        servicio.setPrecioReferencial(precio == null ? null : new BigDecimal(precio));
        return servicio;
    }
}
