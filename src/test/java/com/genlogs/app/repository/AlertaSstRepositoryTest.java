package com.genlogs.app.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.enums.EtapaDespacho;
import com.genlogs.app.enums.TipoAviso;
import com.genlogs.app.model.Notificacion;

/**
 * Valida contra H2 que las consultas JPQL de las alertas de PASE y SCTR arrancan y
 * responden. La lógica de tramos y avisos está en AlertasDocumentosSstServiceTest.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AlertaSstRepositoryTest {

    @Autowired private AlertaSstRepository alertas;
    @Autowired private NotificacionRepository notificaciones;

    @Test
    void laConsultaDeDocumentosPorVencerFunciona() {
        LocalDate hoy = LocalDate.of(2026, 11, 12);

        assertTrue(alertas.porVencer(hoy, hoy.plusDays(30), EtapaDespacho.ENTREGADO).isEmpty());
    }

    @Test
    void cuentaLosAvisosPorTipoYTitulo() {
        String titulo = "PASE del despacho #1 vence el 12/12/2026 (aviso de 30 días)";
        assertEquals(0, alertas.contarAvisos(TipoAviso.DOCUMENTO_POR_VENCER.name(), titulo));

        Notificacion aviso = new Notificacion();
        aviso.setIdUsuario(1L);
        aviso.setTipo(TipoAviso.DOCUMENTO_POR_VENCER.name());
        aviso.setTitulo(titulo);
        aviso.setFechaCreacion(LocalDateTime.now());
        notificaciones.saveAndFlush(aviso);

        assertEquals(1, alertas.contarAvisos(TipoAviso.DOCUMENTO_POR_VENCER.name(), titulo));
        assertEquals(0, alertas.contarAvisos(TipoAviso.GENERAL.name(), titulo));
    }
}
