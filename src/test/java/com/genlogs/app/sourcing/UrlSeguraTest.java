package com.genlogs.app.sourcing;

import com.genlogs.app.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class UrlSeguraTest {

    @ParameterizedTest
    @ValueSource(strings = {"ftp://tienda.com/x", "javascript:alert(1)", "tienda.com/sin-esquema", ""})
    void rechazaUrlsQueNoSonHttp(String url) {
        assertThatThrownBy(() -> UrlSegura.validarFormato(url)).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://sitio.com:6379/", "https://sitio.com:8443/x", "http://sitio.com:22/"})
    void soloAceptaLosPuertos80y443(String url) {
        assertThatThrownBy(() -> UrlSegura.validarFormato(url))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("puerto");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://sitio.com/", "http://sitio.com:80/", "https://sitio.com:443/x"})
    void aceptaHttpYHttpsEnSusPuertos(String url) {
        assertThat(UrlSegura.validarFormato(url).getHost()).isEqualTo("sitio.com");
    }

    @Test
    void rechazaUsuarioYContrasenaEnLaUrl() {
        assertThatThrownBy(() -> UrlSegura.validarFormato("http://user:pw@sitio.com/"))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:80/", "http://127.0.0.1/", "http://[::1]/",
            "http://10.0.0.5/", "http://172.16.0.1/", "http://192.168.1.1/",
            "http://169.254.169.254/", "http://100.64.0.1/", "http://0.0.0.0/",
            "http://[fd00::1]/", "http://[fc00::1]/", "http://[fe80::1]/", "http://[::ffff:10.0.0.1]/"
    })
    void noLeeDireccionesInternas(String url) {
        assertThatThrownBy(() -> UrlSegura.validarDestino(URI.create(url)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("internas");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://8.8.8.8/", "http://100.128.0.1/", "http://[2606:4700:4700::1111]/"})
    void permiteDireccionesPublicas(String url) {
        assertThatNoException().isThrownBy(() -> UrlSegura.validarDestino(URI.create(url)));
    }

    @Test
    void imagenOpcional_soloHttp() {
        assertThat(UrlSegura.imagenOpcional(null)).isNull();
        assertThat(UrlSegura.imagenOpcional("  ")).isNull();
        assertThat(UrlSegura.imagenOpcional("https://img.tienda.com/a.jpg")).isEqualTo("https://img.tienda.com/a.jpg");
        assertThatThrownBy(() -> UrlSegura.imagenOpcional("javascript:alert(1)"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("imagen");
    }
}
