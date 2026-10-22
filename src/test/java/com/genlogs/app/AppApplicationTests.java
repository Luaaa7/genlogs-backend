package com.genlogs.app;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import com.genlogs.app.repository.CondicionPagoRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.EstadoCotizacionRepository;
import com.genlogs.app.service.CotizacionService;

/**
 * Arranque del contexto de Spring: valida el cableado de beans (servicios,
 * repositorios, TransactionTemplate) y que las consultas derivadas nuevas
 * (findFirstByDiasCreditoAndStatusOrderByIdCondicionPagoAsc,
 * findFirstByCodigoEstadoIgnoreCase) sean válidas para el framework.
 */
@SpringBootTest
@ActiveProfiles("test")
class AppApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CotizacionService cotizacionService;

    @Autowired
    private CotizacionRepository cotizacionRepository;

    @Autowired
    private CondicionPagoRepository condicionPagoRepository;

    @Autowired
    private EstadoCotizacionRepository estadoCotizacionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void contextLoads() {
        assertNotNull(context);
        assertNotNull(cotizacionService);
        assertNotNull(cotizacionRepository);
        assertNotNull(condicionPagoRepository);
        assertNotNull(estadoCotizacionRepository);
        assertNotNull(transactionManager);
    }
}
