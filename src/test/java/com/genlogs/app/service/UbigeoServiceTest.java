package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Auditable;
import com.genlogs.app.model.Departamento;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.Pais;
import com.genlogs.app.model.Provincia;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.repository.DepartamentoRepository;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.PaisRepository;
import com.genlogs.app.repository.ProvinciaRepository;

/** D-15: texto de Factiliza contra el catálogo del INEI y distrito obligatorio solo para Perú. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UbigeoServiceTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(700);

    @Autowired private UbigeoService ubigeoService;
    @Autowired private PaisRepository paisRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private ProvinciaRepository provinciaRepository;
    @Autowired private DistritoRepository distritoRepository;

    @BeforeEach
    void catalogoNuevo() {
        // Cada prueba agrega distritos: el índice en memoria se vuelve a armar.
        ubigeoService.olvidarIndice();
    }

    @Test
    void rucDeIcaConNazcaQuedaEnElDistritoCorrecto() {
        int n = SECUENCIA.incrementAndGet();
        String departamento = "Ica " + n;
        Distrito nasca = distrito(departamento, "Nazca", "Nazca", n);
        distrito(departamento, "Nazca", "Marcona", n + 1000);

        // Factiliza escribe "NASCA" en mayúsculas; el INEI, "Nazca".
        assertEquals(nasca.getIdDistrito(),
                ubigeoService.buscarIdDistrito(departamento.toUpperCase(), "NASCA", "NASCA").orElseThrow());
        assertEquals(nasca.getIdDistrito(),
                ubigeoService.buscarIdDistrito(departamento, "Nazca", "nazca").orElseThrow());
    }

    @Test
    void conLaProvinciaMalEscritaAceptaElDistritoSiEsUnicoEnElDepartamento() {
        int n = SECUENCIA.incrementAndGet();
        String departamento = "Arequipa " + n;
        Distrito cayma = distrito(departamento, "Arequipa", "Cayma", n);

        assertEquals(cayma.getIdDistrito(),
                ubigeoService.buscarIdDistrito(departamento, "AREQUIPA METROPOLITANA", "CAYMA").orElseThrow());
        assertTrue(ubigeoService.buscarIdDistrito(departamento, "Arequipa", "No Existe").isEmpty());
        assertTrue(ubigeoService.buscarIdDistrito(null, "Arequipa", "Cayma").isEmpty());
    }

    @Test
    void siElDistritoSeRepiteEnElDepartamentoNoAdivina() {
        int n = SECUENCIA.incrementAndGet();
        String departamento = "Lima " + n;
        distrito(departamento, "Lima", "San Isidro", n);
        distrito(departamento, "Huaura", "San Isidro", n + 1000);

        assertTrue(ubigeoService.buscarIdDistrito(departamento, "Otra", "San Isidro").isEmpty());
    }

    @Test
    void enPeruElDistritoEsObligatorio() {
        Pais peru = peru();
        Tercero tercero = new Tercero();

        BusinessException error = assertThrows(BusinessException.class,
                () -> ubigeoService.aplicarUbicacion(tercero, peru.getIdPais(), null, null, null, null));
        assertTrue(error.getMessage().contains("distrito"));
        // Sin país también se asume Perú.
        assertThrows(BusinessException.class,
                () -> ubigeoService.aplicarUbicacion(tercero, null, null, null, null, null));

        int n = SECUENCIA.incrementAndGet();
        Distrito distrito = distrito("Cusco " + n, "Cusco", "Wanchaq", n);
        ubigeoService.aplicarUbicacion(tercero, peru.getIdPais(), distrito.getIdDistrito(), "Texto", "Ignorado", "000");
        assertEquals(distrito.getIdDistrito(), tercero.getDistrito().getIdDistrito());
        assertNull(tercero.getRegionExtranjero());
        assertNull(tercero.getCiudadExtranjero());
    }

    @Test
    void unExtranjeroNoNecesitaDistritoYGuardaSuUbicacionEnTexto() {
        int n = SECUENCIA.incrementAndGet();
        Pais chile = paisRepository.save(auditar(new Pais(), e -> {
            e.setCodigoIso(String.format("C%02d", n % 100));
            e.setNombrePais("Chile " + n);
        }));
        Tercero tercero = new Tercero();

        ubigeoService.aplicarUbicacion(tercero, chile.getIdPais(), null, " Antofagasta ", "Calama", "1390000");

        assertNull(tercero.getDistrito());
        assertEquals(chile.getIdPais(), tercero.getPais().getIdPais());
        assertEquals("Antofagasta", tercero.getRegionExtranjero());
        assertEquals("Calama", tercero.getCiudadExtranjero());
        assertEquals("1390000", tercero.getCodigoPostalExtranjero());
    }

    private Pais peru() {
        return paisRepository.findByCodigoIso("PE")
                .orElseGet(() -> paisRepository.save(auditar(new Pais(), e -> {
                    e.setCodigoIso("PE");
                    e.setNombrePais("Perú");
                })));
    }

    private Distrito distrito(String departamentoNombre, String provinciaNombre, String distritoNombre, int n) {
        Pais pais = peru();
        Departamento departamento = departamentoRepository.save(auditar(new Departamento(), e -> {
            e.setPais(pais);
            e.setNombreDepartamento(departamentoNombre);
        }));
        Provincia provincia = provinciaRepository.save(auditar(new Provincia(), e -> {
            e.setDepartamento(departamento);
            e.setNombreProvincia(provinciaNombre);
        }));
        return distritoRepository.save(auditar(new Distrito(), e -> {
            e.setProvincia(provincia);
            e.setUbigeo(String.format("%06d", n));
            e.setNombreDistrito(distritoNombre);
        }));
    }

    private <T extends Auditable> T auditar(T entidad, Consumer<T> completar) {
        completar.accept(entidad);
        entidad.setUserCreate("test");
        entidad.setProcessCreate("TEST");
        entidad.setDateCreate(LocalDateTime.now());
        if (entidad.getStatus() == null) {
            entidad.setStatus("A");
        }
        return entidad;
    }
}
