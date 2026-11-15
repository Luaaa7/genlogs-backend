package com.genlogs.app.fuentes.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** K2 (v3.3): cifrado AES-GCM de las credenciales y arranque sin clave. */
@ExtendWith(OutputCaptureExtension.class)
class CifradoFuentesTest {

    private static final String CLAVE = "lhBbMwpDD65Od18o2QRkMs1Xs5YJEePy/dHdYxhCpJA=";
    private static final String OTRA_CLAVE = Base64.getEncoder().encodeToString(new byte[32]);

    private final CifradoFuentes cifrado = new CifradoFuentes(CLAVE);

    @Test
    void cifraYDescifraConElMismoContexto() {
        String guardado = cifrado.cifrar("MiClave-Secreta.123", "EBAY:CLAVE");
        assertThat(guardado).startsWith("v1:");
        assertThat(cifrado.descifrar(guardado, "EBAY:CLAVE")).isEqualTo("MiClave-Secreta.123");
    }

    @Test
    void elTextoYLaClaveNoAparecenEnLoGuardado() {
        String guardado = cifrado.cifrar("MiClave-Secreta.123", "EBAY:CLAVE");
        assertThat(guardado).doesNotContain("MiClave").doesNotContain("Secreta").doesNotContain(CLAVE);
    }

    @Test
    void elMismoValorCifradoDosVecesDaResultadosDistintos() {
        assertThat(cifrado.cifrar("igual", "EBAY:CLAVE")).isNotEqualTo(cifrado.cifrar("igual", "EBAY:CLAVE"));
    }

    @Test
    void elValorDeUnaFilaNoSeDescifraEnOtra() {
        String guardado = cifrado.cifrar("MiClave", "EBAY:CLAVE");
        assertThatThrownBy(() -> cifrado.descifrar(guardado, "AMAZON:CLAVE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AMAZON:CLAVE")
                .hasMessageNotContaining("MiClave");
    }

    @Test
    void unDatoAlteradoOConOtraClaveNoSeDescifra() {
        String guardado = cifrado.cifrar("MiClave", "EBAY:CLAVE");
        // Se cambia un byte real del texto cifrado (no un carácter de relleno del Base64).
        byte[] bytes = Base64.getDecoder().decode(guardado.substring("v1:".length()));
        bytes[bytes.length - 1] ^= 0x01;
        String alterado = "v1:" + Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cifrado.descifrar(alterado, "EBAY:CLAVE")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CifradoFuentes(OTRA_CLAVE).descifrar(guardado, "EBAY:CLAVE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GENLOGS_FUENTES_CLAVE");
        assertThatThrownBy(() -> cifrado.descifrar("texto plano", "EBAY:CLAVE")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sinLaClaveNoArrancaYLoDiceEnElLog(CapturedOutput salida) {
        assertThatThrownBy(() -> new CifradoFuentes(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falta la variable GENLOGS_FUENTES_CLAVE");
        assertThat(salida.getOut() + salida.getErr()).contains("Falta la variable GENLOGS_FUENTES_CLAVE");
    }

    @Test
    void unaClaveQueNoEsBase64ONoMide32BytesTampocoArranca() {
        assertThatThrownBy(() -> new CifradoFuentes("no-es-base64!!")).hasMessageContaining("no está en Base64");
        assertThatThrownBy(() -> new CifradoFuentes(Base64.getEncoder().encodeToString(new byte[16])))
                .hasMessageContaining("debe tener 32 bytes");
    }

    @Test
    void elContextoDeSpringNoArrancaSinLaVariable() {
        new ApplicationContextRunner()
                .withBean(CifradoFuentes.class)
                .run(contexto -> assertThat(contexto).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("Falta la variable GENLOGS_FUENTES_CLAVE"));
        new ApplicationContextRunner()
                .withPropertyValues("genlogs.fuentes.clave=" + CLAVE)
                .withBean(CifradoFuentes.class)
                .run(contexto -> assertThat(contexto).hasNotFailed().hasSingleBean(CifradoFuentes.class));
    }
}
