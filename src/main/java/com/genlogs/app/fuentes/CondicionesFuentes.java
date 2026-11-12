package com.genlogs.app.fuentes;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Una fuente solo se crea si sus dos credenciales tienen texto. Con la variable de entorno
 * vacía (el valor por defecto) la fuente no existe y SourcingService sigue sin ella.
 */
public final class CondicionesFuentes {

    private CondicionesFuentes() {
    }

    static boolean conTexto(ConditionContext contexto, String propiedad) {
        String valor = contexto.getEnvironment().getProperty(propiedad);
        return valor != null && !valor.isBlank();
    }

    public static class EbayConfigurado implements Condition {
        @Override
        public boolean matches(ConditionContext contexto, AnnotatedTypeMetadata metadata) {
            return conTexto(contexto, "genlogs.fuentes.ebay.client-id")
                    && conTexto(contexto, "genlogs.fuentes.ebay.client-secret");
        }
    }
}
