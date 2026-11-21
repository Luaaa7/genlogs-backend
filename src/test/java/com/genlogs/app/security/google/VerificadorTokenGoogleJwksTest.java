package com.genlogs.app.security.google;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.genlogs.app.exception.BusinessException;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;

/**
 * Verificación del token de Google (ID token) sin red: las claves públicas se
 * entregan con un ClavesGoogle de prueba y los tokens se firman aquí con RS256.
 */
class VerificadorTokenGoogleJwksTest {

    private static final String CLIENTE = "123-abc.apps.googleusercontent.com";
    private static KeyPair par;
    private static KeyPair otroPar;

    @BeforeAll
    static void generarClaves() throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(2048);
        par = generador.generateKeyPair();
        otroPar = generador.generateKeyPair();
    }

    private final ClavesGoogle claves = kid -> "k1".equals(kid) ? par.getPublic() : null;
    private final VerificadorTokenGoogleJwks verificador = new VerificadorTokenGoogleJwks(claves, CLIENTE);

    private JwtBuilder base() {
        long ahora = System.currentTimeMillis();
        return Jwts.builder()
                .header().keyId("k1").and()
                .issuer("https://accounts.google.com")
                .audience().add(CLIENTE).and()
                .subject("sub-123")
                .claim("email", "Ana.Ruiz@Gmail.com")
                .claim("email_verified", true)
                .claim("name", "Ana Ruiz")
                .issuedAt(new Date(ahora))
                .expiration(new Date(ahora + 5 * 60_000));
    }

    private static String firmar(JwtBuilder builder, PrivateKey clave) {
        return builder.signWith(clave, Jwts.SIG.RS256).compact();
    }

    @Test
    void tokenValidoDevuelveLaCuentaConElCorreoEnMinusculas() {
        CuentaGoogle cuenta = verificador.verificar(firmar(base(), par.getPrivate()));

        assertEquals("sub-123", cuenta.sub());
        assertEquals("ana.ruiz@gmail.com", cuenta.correo());
        assertEquals("Ana Ruiz", cuenta.nombre());
    }

    @Test
    void aceptaElEmisorSinHttps() {
        CuentaGoogle cuenta = verificador.verificar(firmar(base().issuer("accounts.google.com"), par.getPrivate()));
        assertEquals("sub-123", cuenta.sub());
    }

    @Test
    void rechazaTokenParaOtraAplicacion() {
        String token = firmar(base().audience().add("otra-app").and(), par.getPrivate());
        // La audiencia trae las dos; se rechaza solo si no está la nuestra.
        assertEquals("sub-123", verificador.verificar(token).sub());

        String ajeno = firmar(Jwts.builder()
                .header().keyId("k1").and()
                .issuer("https://accounts.google.com")
                .audience().add("otra-app").and()
                .subject("sub-123")
                .claim("email", "a@b.pe")
                .claim("email_verified", true)
                .expiration(new Date(System.currentTimeMillis() + 60_000)), par.getPrivate());
        assertThrows(BusinessException.class, () -> verificador.verificar(ajeno));
    }

    @Test
    void rechazaOtroEmisor() {
        String token = firmar(base().issuer("https://evil.example.com"), par.getPrivate());
        assertThrows(BusinessException.class, () -> verificador.verificar(token));
    }

    @Test
    void rechazaTokenVencido() {
        long hace = System.currentTimeMillis() - 3_600_000;
        String token = firmar(base().issuedAt(new Date(hace - 60_000)).expiration(new Date(hace)), par.getPrivate());
        assertThrows(BusinessException.class, () -> verificador.verificar(token));
    }

    @Test
    void rechazaFirmaConOtraClave() {
        String token = firmar(base(), otroPar.getPrivate());
        assertThrows(BusinessException.class, () -> verificador.verificar(token));
    }

    @Test
    void rechazaClaveDesconocida() {
        String token = firmar(base().header().keyId("k-desconocida").and(), par.getPrivate());
        assertThrows(BusinessException.class, () -> verificador.verificar(token));
    }

    @Test
    void rechazaCorreoNoVerificado() {
        String token = firmar(base().claim("email_verified", false), par.getPrivate());
        BusinessException ex = assertThrows(BusinessException.class, () -> verificador.verificar(token));
        assertTrue(ex.getMessage().contains("correo verificado"));
    }

    @Test
    void rechazaTextoQueNoEsToken() {
        assertThrows(BusinessException.class, () -> verificador.verificar("no-es-un-token"));
        assertThrows(BusinessException.class, () -> verificador.verificar(""));
        assertThrows(BusinessException.class, () -> verificador.verificar(null));
    }

    @Test
    void sinClientIdElIngresoConGoogleNoEstaDisponible() {
        VerificadorTokenGoogleJwks apagado = new VerificadorTokenGoogleJwks(claves, "  ");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> apagado.verificar(firmar(base(), par.getPrivate())));
        assertTrue(ex.getMessage().contains("no está disponible"));
    }

    @Test
    void parseaLasClavesPublicasDeGoogle() {
        RSAPublicKey publica = (RSAPublicKey) par.getPublic();
        String json = """
                {"keys":[
                  {"kty":"RSA","alg":"RS256","use":"sig","kid":"k1","n":"%s","e":"%s"},
                  {"kty":"EC","kid":"no-rsa","crv":"P-256","x":"AA","y":"AA"}
                ]}
                """.formatted(b64(publica.getModulus()), b64(publica.getPublicExponent()));

        Map<String, Key> mapa = ClavesGoogleHttp.parsear(json);

        assertEquals(1, mapa.size());
        RSAPublicKey leida = (RSAPublicKey) mapa.get("k1");
        assertEquals(publica.getModulus(), leida.getModulus());
        assertEquals(publica.getPublicExponent(), leida.getPublicExponent());
        assertNull(mapa.get("no-rsa"));
    }

    private static String b64(BigInteger valor) {
        byte[] bytes = valor.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] sinCero = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, sinCero, 0, sinCero.length);
            bytes = sinCero;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
