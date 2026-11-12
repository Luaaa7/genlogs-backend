package com.genlogs.app.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

        verify(servicio).avisar();
    }

    @Test
    void alArrancarNoCorreSalvoQueSeActive() {
        new AlertasDocumentosSstTarea(servicio, false).alArrancar();
        verify(servicio, never()).avisar();

        new AlertasDocumentosSstTarea(servicio, true).alArrancar();
        verify(servicio).avisar();
    }

    @Test
    void unErrorNoTumbaElPlanificador() {
        doThrow(new IllegalStateException("BD caída")).when(servicio).avisar();

        new AlertasDocumentosSstTarea(servicio, false).diaria();
    }
}
