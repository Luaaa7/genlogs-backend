package com.genlogs.app.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Aplica {@link PoliticaContrasena} al alta del portal. */
@Documented
@Constraint(validatedBy = ContrasenaDeRegistroValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ContrasenaDeRegistro {

    String message() default "La contraseña no cumple las reglas";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
