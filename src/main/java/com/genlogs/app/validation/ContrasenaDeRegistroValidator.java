package com.genlogs.app.validation;

import java.util.Optional;

import com.genlogs.app.dto.RegistroClienteRequest;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** La contraseña en blanco la rechaza {@code @NotBlank}; aquí van el resto de reglas. */
public class ContrasenaDeRegistroValidator implements ConstraintValidator<ContrasenaDeRegistro, RegistroClienteRequest> {

    @Override
    public boolean isValid(RegistroClienteRequest value, ConstraintValidatorContext context) {
        if (value == null || value.getPassword() == null || value.getPassword().isBlank()) {
            return true;
        }
        Optional<String> fallo = PoliticaContrasena.incumplimiento(value.getPassword(), value.getCorreo());
        if (fallo.isEmpty()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(fallo.get())
                .addPropertyNode("password")
                .addConstraintViolation();
        return false;
    }
}
