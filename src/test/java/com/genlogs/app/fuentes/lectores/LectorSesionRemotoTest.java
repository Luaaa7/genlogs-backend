package com.genlogs.app.fuentes.lectores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genlogs.app.fuentes.config.CampoSelector;
import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.FuenteSelector;
import com.genlogs.app.fuentes.config.FuenteSelectorRepository;
import com.genlogs.app.fuentes.config.PaginaSelector;
import com.genlogs.app.fuentes.config.ResultadoSesion;
import com.genlogs.app.sourcing.lector.ContratoLector;
import com.genlogs.app.sourcing.lector.LectorRemotoClient;
import com.genlogs.app.sourcing.lector.LectorRemotoException;

/**
 * L1 (v3.3, BPMN 9-A) · «Probar sesión» de verdad: el adaptador abre la página de ingreso de la
 * tienda con sus reglas LOGIN y la cuenta de búsqueda, y traduce lo que responde el servicio de lectura.
 */
class LectorSesionRemotoTest {

    private static final String USUARIO = "compras@genlogs.example";
    private static final String CLAVE = "clave-secreta-de-la-tienda";
    private static final String COOKIE = "valor-de-cookie-secreto";

    private LectorRemotoClient cliente;
    private FuenteSelectorRepository selectores;
    private LectorSesionRemoto lector;

    @BeforeEach
    void preparar() {
        cliente = mock(LectorRemotoClient.class);
        selectores = mock(FuenteSelectorRepository.class);
        lector = new LectorSesionRemoto(cliente, selectores,
                Clock.fixed(Instant.parse("2026-11-17T05:00:00Z"), ZoneOffset.UTC));
    }

    private static FuenteSelector regla(PaginaSelector pagina, CampoSelector campo, String css) {
        FuenteSelector s = new FuenteSelector();
        s.setPagina(pagina);
        s.setCampo(campo);
        s.setRegla(css);
        return s;
    }

    private void conReglas(FuenteSelector... reglas) {
        when(selectores.findByFuente_CodigoAndStatus(CodigoFuente.EBAY, "A")).thenReturn(List.of(reglas));
    }

    private void conLasCuatroReglasLogin() {
        conReglas(
                regla(PaginaSelector.LOGIN, CampoSelector.USUARIO, "#userid"),
                regla(PaginaSelector.LOGIN, CampoSelector.CLAVE, "#pass"),
                regla(PaginaSelector.LOGIN, CampoSelector.ENVIAR, "#sgnBt"),
                regla(PaginaSelector.LOGIN, CampoSelector.EXITO, "#gh-ug"),
                regla(PaginaSelector.BUSQUEDA, CampoSelector.ITEM, "li.s-item"));
    }

    private static ContratoLector.Respuesta conSesion(String validaHasta) throws Exception {
        var storage = new ObjectMapper().readTree(
                "{\"cookies\":[{\"name\":\"s\",\"value\":\"" + COOKIE + "\",\"domain\":\".ebay.com\"}],\"origins\":[]}");
        return new ContratoLector.Respuesta(ContratoLector.Estado.OK, List.of(),
                new ContratoLector.Sesion(validaHasta, storage));
    }

    @Test
    void sinLasCuatroReglasLoginNoLlamaAlServicioDeLectura() {
        conReglas(
                regla(PaginaSelector.LOGIN, CampoSelector.USUARIO, "#userid"),
                regla(PaginaSelector.LOGIN, CampoSelector.CLAVE, "#pass"));

        ResultadoSesion r = lector.iniciarSesion(CodigoFuente.EBAY, "https://www.ebay.com", USUARIO, CLAVE);

        assertThat(r.resultado()).isEqualTo(ResultadoSesion.Resultado.ERROR);
        assertThat(r.mensaje()).startsWith("eBay:").contains("Reglas de extracción");
        verifyNoInteractions(cliente);
    }

    @Test
    void mandaLaPaginaDeIngresoSoloLasReglasLoginYLaCuentaSinSesion() throws Exception {
        conLasCuatroReglasLogin();
        when(cliente.iniciarSesion(any())).thenReturn(conSesion("2026-11-17T06:00:00Z"));

        lector.iniciarSesion(CodigoFuente.EBAY, "https://www.ebay.com", USUARIO, CLAVE);

        ArgumentCaptor<ContratoLector.Peticion> captor = ArgumentCaptor.forClass(ContratoLector.Peticion.class);
        verify(cliente).iniciarSesion(captor.capture());
        ContratoLector.Peticion p = captor.getValue();
        assertThat(p.fuente()).isEqualTo("EBAY");
        assertThat(p.url()).isEqualTo(LectorSesionRemoto.PAGINA_INGRESO.get(CodigoFuente.EBAY));
        assertThat(p.reglas()).isEqualTo(Map.of("login",
                Map.of("usuario", "#userid", "clave", "#pass", "enviar", "#sgnBt", "exito", "#gh-ug")));
        assertThat(p.sesion()).isNull();
        assertThat(p.credenciales().usuario()).isEqualTo(USUARIO);
        assertThat(p.credenciales().clave()).isEqualTo(CLAVE);
    }

    @Test
    void conSesionQuedaOkConLaSesionGuardableYSuVencimiento() throws Exception {
        conLasCuatroReglasLogin();
        when(cliente.iniciarSesion(any())).thenReturn(conSesion("2026-11-17T06:00:00Z"));

        ResultadoSesion r = lector.iniciarSesion(CodigoFuente.EBAY, "https://www.ebay.com", USUARIO, CLAVE);

        assertThat(r.resultado()).isEqualTo(ResultadoSesion.Resultado.OK);
        assertThat(r.validaHasta()).isEqualTo(LocalDateTime.of(2026, 11, 17, 6, 0));
        // La sesión guardada es la que después leen los lectores al buscar.
        ContratoLector.Sesion leida = PuenteLector.sesion(r.sesion(), Clock.systemUTC());
        assertThat(leida.validaHasta()).isEqualTo("2026-11-17T06:00:00Z");
        assertThat(leida.storageState().path("cookies").get(0).path("value").asText()).isEqualTo(COOKIE);
        assertThat(r.toString()).doesNotContain(COOKIE).doesNotContain(CLAVE);
    }

    @Test
    void siLaTiendaPideCodigoQuedaEnVerificacion() {
        conLasCuatroReglasLogin();
        when(cliente.iniciarSesion(any()))
                .thenThrow(new LectorRemotoException(ContratoLector.Estado.VERIFICACION));

        ResultadoSesion r = lector.iniciarSesion(CodigoFuente.EBAY, "https://www.ebay.com", USUARIO, CLAVE);

        assertThat(r.resultado()).isEqualTo(ResultadoSesion.Resultado.VERIFICACION);
        assertThat(r.mensaje()).startsWith("eBay:");
    }

    @ParameterizedTest
    @EnumSource(value = ContratoLector.Estado.class,
            names = {"SESION_VENCIDA", "BLOQUEO", "NO_CONFIGURADO", "TIEMPO_AGOTADO", "URL_NO_PERMITIDA"})
    void losDemasErroresQuedanComoErrorConMensajeSeguro(ContratoLector.Estado estado) {
        conLasCuatroReglasLogin();
        LectorRemotoException error = new LectorRemotoException(estado);
        when(cliente.iniciarSesion(any())).thenThrow(error);

        ResultadoSesion r = lector.iniciarSesion(CodigoFuente.EBAY, "https://www.ebay.com", USUARIO, CLAVE);

        assertThat(r.resultado()).isEqualTo(ResultadoSesion.Resultado.ERROR);
        assertThat(r.mensaje()).isEqualTo("eBay: " + error.getMessage());
        assertThat(r.sesion()).isNull();
    }

    @Test
    void unErrorInesperadoDelClienteQuedaComoErrorSinDetalle() {
        conLasCuatroReglasLogin();
        when(cliente.iniciarSesion(any())).thenThrow(new IllegalStateException("detalle interno " + CLAVE));

        ResultadoSesion r = lector.iniciarSesion(CodigoFuente.EBAY, "https://www.ebay.com", USUARIO, CLAVE);

        assertThat(r.resultado()).isEqualTo(ResultadoSesion.Resultado.ERROR);
        assertThat(r.mensaje()).isEqualTo("eBay: no se pudo iniciar sesión con el servicio de lectura.");
        assertThat(r.sesion()).isNull();
    }

    @Test
    void unaSesionSinVencimientoValidoNoSeGuarda() throws Exception {
        conLasCuatroReglasLogin();
        when(cliente.iniciarSesion(any())).thenReturn(conSesion("mañana"));

        ResultadoSesion r = lector.iniciarSesion(CodigoFuente.EBAY, "https://www.ebay.com", USUARIO, CLAVE);

        assertThat(r.resultado()).isEqualTo(ResultadoSesion.Resultado.ERROR);
        assertThat(r.sesion()).isNull();
        assertThat(r.mensaje()).doesNotContain(COOKIE);
    }

    @Test
    void lasCincoTiendasTienenSuPaginaDeIngresoConHttps() {
        List<CodigoFuente> sinPagina = new ArrayList<>();
        for (CodigoFuente c : CodigoFuente.values()) {
            String url = LectorSesionRemoto.PAGINA_INGRESO.get(c);
            if (url == null || !url.startsWith("https://")) {
                sinPagina.add(c);
            }
        }
        assertThat(sinPagina).isEmpty();
    }
}
