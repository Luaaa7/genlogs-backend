package com.genlogs.app.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.genlogs.app.dto.RegistroClienteRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class PoliticaContrasenaTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void nulaOVaciaLaRechazaLaPoliticaSinIncluirla() {
        assertThat(PoliticaContrasena.incumplimiento(null, "ana@portal.pe"))
                .contains("La contraseña es obligatoria");
        assertThat(PoliticaContrasena.incumplimiento("   ", "ana@portal.pe"))
                .contains("La contraseña es obligatoria");
    }

    @Test
    void laLongitudLaDefineSoloLaPolitica() {
        RegistroClienteRequest request = request("Ab1def");

        Set<String> mensajes = validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());

        assertThat(mensajes).contains("La contraseña debe tener al menos 8 caracteres");
        assertThat(mensajes).doesNotContain("La contraseña debe tener entre 8 y 72 caracteres");
        assertThat(String.join(" ", mensajes)).doesNotContain("Ab1def");
    }

    @Test
    void elTopeSeMideEnBytesYElMensajeLoExplica() {
        String mensaje = "La contraseña es demasiado larga (máximo 72 bytes; las tildes y la ñ cuentan doble)";
        // 73 caracteres ASCII: pasa el tope por caracteres.
        assertThat(PoliticaContrasena.incumplimiento("Aa1" + "x".repeat(70), "ana@portal.pe")).contains(mensaje);
        // 43 caracteres, pero 83 bytes: 40 «ñ» cuentan doble.
        assertThat(PoliticaContrasena.incumplimiento("ñ".repeat(40) + "Aa1", "ana@portal.pe")).contains(mensaje);
        // Justo 72 bytes: se acepta.
        assertThat(PoliticaContrasena.incumplimiento("Aa1" + "x".repeat(69), "ana@portal.pe")).isEmpty();
    }

    private static RegistroClienteRequest request(String password) {
        RegistroClienteRequest request = new RegistroClienteRequest();
        request.setRazonSocial("Empresa");
        request.setDocumento("20123456789");
        request.setNombreContacto("Ana");
        request.setCorreo("ana@portal.pe");
        request.setPassword(password);
        return request;
    }
}