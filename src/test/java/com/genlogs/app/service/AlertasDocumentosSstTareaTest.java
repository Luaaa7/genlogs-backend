package com.genlogs.app.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertasDocumentosSstTareaTest {

    @Mock private AlertasDocumentosSstService servicio;

    @Test
    void laTareaDiariaUsaLaFechaDeLima() {
        new AlertasDocumentosSstTarea(servicio, false).diaria();

        verify(servicio).avisar(LocalDate.now(PlazoDespacho.LIMA));
    }

    @Test
    void alArrancarNoCorreSalvoQueSeActive() {
        new AlertasDocumentosSstTarea(servicio, false).alArrancar();
        verify(servicio, never()).avisar(any());

        new AlertasDocumentosSstTarea(servicio, true).alArrancar();
        verify(servicio).avisar(any());
    }

    @Test
    void unErrorNoTumbaElPlanificador() {
        when(servicio.avisar(any())).thenThrow(new IllegalStateException("BD caída"));

        new AlertasDocumentosSstTarea(servicio, false).diaria();
    }
}
