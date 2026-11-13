package com.genlogs.app.fuentes.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.exception.ResourceNotFoundException;

import jakarta.persistence.EntityManager;

/**
 * K2 (v3.3), criterio del documento: la clave nunca aparece en texto plano en la base.
 * Se guarda por el servicio y se lee la tabla fuente_credencial directo con SQL.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CredencialesFuenteServiceTest {

    private static final String CLAVE_TIENDA = "Clave-De-La-Tienda.2026";

    @Autowired private CredencialesFuenteService servicio;
    @Autowired private FuenteBusquedaConfigRepository fuenteRepository;
    @Autowired private FuenteSelectorRepository selectorRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @BeforeEach
    void lasCincoFuentesComoLasDejaV33() {
        for (CodigoFuente codigo : CodigoFuente.values()) {
            if (fuenteRepository.findByCodigo(codigo).isEmpty()) {
                FuenteBusquedaConfig fuente = new FuenteBusquedaConfig();
                fuente.setCodigo(codigo);
                fuente.setNombre(codigo.name());
                fuente.setUrlBase("https://" + codigo.name().toLowerCase() + ".test");
                fuente.setUserCreate("test");
                fuente.setProcessCreate("TEST");
                fuenteRepository.save(fuente);
            }
        }
    }

    @Test
    void lasFuentesNacenInactivasYSinProbar() {
        FuenteBusquedaConfig ebay = fuenteRepository.findByCodigo(CodigoFuente.EBAY).orElseThrow();
        assertThat(ebay.getActiva()).isFalse();
        assertThat(ebay.getEstado()).isEqualTo(EstadoFuente.SIN_PROBAR);
        assertThat(fuenteRepository.findByActivaTrueAndStatusOrderByIdFuente("A")).isEmpty();
    }

    @Test
    void laClaveNuncaQuedaEnTextoPlanoEnLaBase() {
        servicio.guardar(CodigoFuente.EBAY, TipoCredencial.CLAVE, CLAVE_TIENDA, 1L, "jefe");

        List<String> enLaBase = jdbc.queryForList("SELECT valor_cifrado FROM fuente_credencial", String.class);
        assertThat(enLaBase).hasSize(1);
        assertThat(enLaBase.get(0)).startsWith("v1:").doesNotContain(CLAVE_TIENDA).doesNotContain("Tienda");
        // Ninguna columna de la fila trae el valor en claro.
        String filaCompleta = jdbc.queryForMap("SELECT * FROM fuente_credencial").toString();
        assertThat(filaCompleta).doesNotContain(CLAVE_TIENDA);

        assertThat(servicio.leer(CodigoFuente.EBAY, TipoCredencial.CLAVE)).contains(CLAVE_TIENDA);
        assertThat(servicio.tiene(CodigoFuente.EBAY, TipoCredencial.CLAVE)).isTrue();
        assertThat(servicio.tiene(CodigoFuente.EBAY, TipoCredencial.USUARIO)).isFalse();
        assertThat(servicio.leer(CodigoFuente.AMAZON, TipoCredencial.CLAVE)).isEmpty();
    }

    @Test
    void cambiarLaClaveReemplazaLaFilaSinDuplicarla() {
        servicio.guardar(CodigoFuente.AMAZON, TipoCredencial.USUARIO, "compras@genlogs.pe", 1L, "jefe");
        servicio.guardar(CodigoFuente.AMAZON, TipoCredencial.USUARIO, "logistica@genlogs.pe", 1L, "jefe");

        Integer filas = jdbc.queryForObject("SELECT COUNT(*) FROM fuente_credencial", Integer.class);
        assertThat(filas).isEqualTo(1);
        assertThat(servicio.leer(CodigoFuente.AMAZON, TipoCredencial.USUARIO)).contains("logistica@genlogs.pe");
    }

    @Test
    void copiarElCifradoDeUnaTiendaAOtraNoLoVuelveLegible() {
        servicio.guardar(CodigoFuente.EBAY, TipoCredencial.CLAVE, CLAVE_TIENDA, 1L, "jefe");
        servicio.guardar(CodigoFuente.ALIBABA, TipoCredencial.CLAVE, "otra", 1L, "jefe");
        String deEbay = jdbc.queryForObject(
                "SELECT c.valor_cifrado FROM fuente_credencial c JOIN fuente_busqueda f ON f.id_fuente = c.id_fuente "
                        + "WHERE f.codigo = 'EBAY'", String.class);
        jdbc.update("UPDATE fuente_credencial SET valor_cifrado = ? WHERE id_fuente = "
                + "(SELECT id_fuente FROM fuente_busqueda WHERE codigo = 'ALIBABA')", deEbay);
        // El cambio se hizo con SQL directo: se olvida la fila que JPA tenía en memoria.
        entityManager.clear();

        assertThatThrownBy(() -> servicio.leer(CodigoFuente.ALIBABA, TipoCredencial.CLAVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(CLAVE_TIENDA);
    }

    @Test
    void noGuardaValoresVaciosNiEnFuentesQueNoExisten() {
        assertThatThrownBy(() -> servicio.guardar(CodigoFuente.EBAY, TipoCredencial.CLAVE, "  ", 1L, "jefe"))
                .isInstanceOf(IllegalArgumentException.class);
        jdbc.update("DELETE FROM fuente_busqueda WHERE codigo = 'MERCADO_LIBRE'");
        assertThatThrownBy(() -> servicio.guardar(CodigoFuente.MERCADO_LIBRE, TipoCredencial.CLAVE, "x", 1L, "jefe"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void lasReglasSeGuardanPorFuentePaginaYCampo() {
        FuenteBusquedaConfig ebay = fuenteRepository.findByCodigo(CodigoFuente.EBAY).orElseThrow();
        FuenteSelector regla = new FuenteSelector();
        regla.setFuente(ebay);
        regla.setPagina(PaginaSelector.FICHA);
        regla.setCampo(CampoSelector.PRECIO);
        regla.setRegla(".x-price-primary span");
        regla.setUserCreate("test");
        regla.setProcessCreate("TEST");
        selectorRepository.save(regla);

        assertThat(selectorRepository.findByFuente_CodigoAndStatus(CodigoFuente.EBAY, "A"))
                .extracting(FuenteSelector::getCampo).containsExactly(CampoSelector.PRECIO);
    }
}
