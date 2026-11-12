package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.genlogs.app.dto.ConfirmarSstRequest;
import com.genlogs.app.enums.EtapaDespacho;
import com.genlogs.app.enums.OrigenRepuesto;
import com.genlogs.app.enums.TipoSstDespacho;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.Despacho;
import com.genlogs.app.model.DespachoSst;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.DespachoRepository;
import com.genlogs.app.repository.DespachoSstRepository;
import com.genlogs.app.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class DespachoSstServiceTest {

    @Mock private DespachoSstRepository validaciones;
    @Mock private DespachoRepository despachos;
    @Mock private UsuarioRepository usuarios;
    @Mock private EntityManager entityManager;

    private Despacho despacho;
    private final List<DespachoSst> filas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Usuario rosa = new Usuario();
        rosa.setIdUsuario(6L);
        rosa.setNombreUsuario("rosa");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("rosa", "n/a", List.of()));
        org.mockito.Mockito.lenient().when(usuarios.findByNombreUsuario("rosa")).thenReturn(Optional.of(rosa));

        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setIdCotizacion(3L);
        OrdenCompra orden = new OrdenCompra();
        orden.setIdOrdenCompra(8L);
        orden.setNumeroOrdenCompra("0008-10");
        orden.setCotizacion(cotizacion);
        despacho = new Despacho();
        despacho.setIdDespacho(15L);
        despacho.setOrdenCompra(orden);
        despacho.setCotizacion(cotizacion);
        despacho.setOrigen(OrigenRepuesto.NACIONAL);
        despacho.setEtapa(EtapaDespacho.EN_PREPARACION);
        despacho.setStatus("A");
        despacho.setUsuario(rosa);
        org.mockito.Mockito.lenient().when(despachos.findById(15L)).thenReturn(Optional.of(despacho));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void corregirLaFechaDejaUnaSolaFilaActivaYHaceFlushAntesDeInsertar() {
        when(validaciones.findByDespacho_IdDespachoAndTipoAndStatus(eq(15L), eq(TipoSstDespacho.PASE), eq("A")))
                .thenAnswer(inv -> filas.stream()
                        .filter(f -> f.getTipo() == TipoSstDespacho.PASE && "A".equals(f.getStatus()))
                        .findFirst());
        when(validaciones.save(any())).thenAnswer(inv -> {
            DespachoSst fila = inv.getArgument(0);
            if (!filas.contains(fila)) {
                filas.add(fila);
            }
            return fila;
        });
        LocalDate primera = LocalDate.now(PlazoDespacho.LIMA).plusDays(10);
        LocalDate corregida = LocalDate.now(PlazoDespacho.LIMA).plusDays(40);

        servicio().confirmar(15L, pedido(TipoSstDespacho.PASE, primera, primera.minusDays(1), "POL-1"));
        servicio().confirmar(15L, pedido(TipoSstDespacho.PASE, corregida, null, "POL-2"));

        List<DespachoSst> activas = filas.stream().filter(f -> "A".equals(f.getStatus())).toList();
        assertEquals(1, activas.size());
        assertEquals(corregida, activas.get(0).getFechaVencimiento());
        assertEquals("POL-2", activas.get(0).getReferencia());
        assertEquals(1, filas.stream().filter(f -> "I".equals(f.getStatus())).count());

        InOrder orden = inOrder(validaciones, entityManager);
        orden.verify(validaciones).save(any(DespachoSst.class));
        orden.verify(validaciones).save(argThat(fila -> "I".equals(fila.getStatus())));
        orden.verify(entityManager).flush();
        orden.verify(validaciones).save(argThat(fila ->
                "A".equals(fila.getStatus()) && corregida.equals(fila.getFechaVencimiento())));
    }

    @Test
    void noSeConfirmaUnDespachoEntregadoNiUnoAnulado() {
        despacho.setEtapa(EtapaDespacho.ENTREGADO);
        BusinessException entregado = assertThrows(BusinessException.class,
                () -> servicio().confirmar(15L, pedido(TipoSstDespacho.SCTR, manana(), null, null)));
        assertTrue(entregado.getMessage().contains("entregado"));
        assertFalse(entregado.getMessage().startsWith(DespachoBloqueoSst.PREFIJO));

        despacho.setEtapa(EtapaDespacho.EN_PREPARACION);
        despacho.setStatus("I");
        BusinessException anulado = assertThrows(BusinessException.class,
                () -> servicio().confirmar(15L, pedido(TipoSstDespacho.PASE, manana(), null, null)));
        assertTrue(anulado.getMessage().contains("anulado"));
        assertFalse(anulado.getMessage().startsWith(DespachoBloqueoSst.PREFIJO));
        verify(validaciones, never()).save(any());
    }

    @Test
    void laEmisionNoPuedeSerPosteriorAlVencimientoNiElVencimientoAnteriorAHoy() {
        LocalDate hoy = LocalDate.now(PlazoDespacho.LIMA);
        BusinessException emision = assertThrows(BusinessException.class,
                () -> servicio().confirmar(15L, pedido(TipoSstDespacho.PASE, hoy, hoy.plusDays(1), null)));
        assertTrue(emision.getMessage().contains("emisión"));

        BusinessException vencimiento = assertThrows(BusinessException.class,
                () -> servicio().confirmar(15L, pedido(TipoSstDespacho.PASE, hoy.minusDays(1), null, null)));
        assertTrue(vencimiento.getMessage().contains("anterior a hoy"));
        verify(validaciones, never()).save(any());
    }

    @Test
    void elMismoDiaDeVencimientoValeYElSiguienteNo() {
        LocalDate hoy = LocalDate.of(2026, 3, 16);
        assertEquals(null, DespachoBloqueoSst.mensaje(hoy, hoy, hoy));
        String siguiente = DespachoBloqueoSst.mensaje(hoy, hoy, hoy.plusDays(1));
        assertEquals("DESPACHO_BLOQUEADO_SST: pase vencido el 16/03/2026 y SCTR vencido el 16/03/2026", siguiente);
    }

    @Test
    void despuesDeConfirmarLosDosLaSalidaYaNoEstaBloqueada() {
        when(validaciones.findByDespacho_IdDespachoAndTipoAndStatus(eq(15L), any(), eq("A")))
                .thenAnswer(inv -> {
                    TipoSstDespacho tipo = inv.getArgument(1);
                    return filas.stream()
                            .filter(f -> f.getTipo() == tipo && "A".equals(f.getStatus()))
                            .findFirst();
                });
        when(validaciones.findByDespacho_IdDespachoAndStatus(15L, "A"))
                .thenAnswer(inv -> filas.stream().filter(f -> "A".equals(f.getStatus())).toList());
        when(validaciones.save(any())).thenAnswer(inv -> {
            DespachoSst fila = inv.getArgument(0);
            if (!filas.contains(fila)) {
                filas.add(fila);
            }
            return fila;
        });
        LocalDate vence = LocalDate.now(PlazoDespacho.LIMA).plusDays(5);
        servicio().confirmar(15L, pedido(TipoSstDespacho.PASE, vence, null, "ABC-1"));
        servicio().confirmar(15L, pedido(TipoSstDespacho.SCTR, vence, null, "SCTR-9"));

        servicio().exigirParaSalida(despacho, EtapaDespacho.EN_TRANSITO, LocalDate.now(PlazoDespacho.LIMA));
        var estado = servicio().estado(despacho, LocalDate.now(PlazoDespacho.LIMA));

        assertEquals(false, estado.isBloqueaAvance());
        assertEquals(null, estado.getMensaje());
        assertEquals("ABC-1", estado.getPase().getReferencia());
        assertEquals("SCTR-9", estado.getSctr().getReferencia());
    }

    private DespachoSstService servicio() {
        return new DespachoSstService(validaciones, despachos, usuarios, entityManager);
    }

    private static LocalDate manana() {
        return LocalDate.now(PlazoDespacho.LIMA).plusDays(1);
    }

    private static ConfirmarSstRequest pedido(TipoSstDespacho tipo, LocalDate vencimiento,
            LocalDate emision, String referencia) {
        ConfirmarSstRequest request = new ConfirmarSstRequest();
        request.setTipo(tipo);
        request.setFechaVencimiento(vencimiento);
        request.setFechaEmision(emision);
        request.setReferencia(referencia);
        return request;
    }
}
