package com.genlogs.app.sourcing.lector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Contrato v1 de P1/P2. Las reglas son datos CSS, nunca código ejecutable. */
public final class ContratoLector {
    private ContratoLector() { }

    public enum Estado {
        OK, VERIFICACION, BLOQUEO, SESION_VENCIDA, TIEMPO_AGOTADO, SIN_RESPUESTA,
        OCUPADO, URL_NO_PERMITIDA, REGLAS_INVALIDAS, SOLICITUD_INVALIDA,
        NO_AUTORIZADO, NO_ENCONTRADO, NO_CONFIGURADO, RESPUESTA_INVALIDA, INTERRUMPIDO
    }

    public record Credenciales(String usuario, String clave) {
        @Override public String toString() { return "Credenciales[REDACTADO]"; }
    }

    public record Sesion(String validaHasta, JsonNode storageState) {
        @Override public String toString() { return "Sesion[REDACTADO]"; }
    }

    /** URL ya construida por el adaptador de la tienda; el lector no inventa selectores ni URLs. */
    public record Peticion(String fuente, String url, Map<String, Object> reglas,
                           Sesion sesion, Credenciales credenciales, Integer maxResultados) {
        @Override public String toString() { return "PeticionLector[REDACTADO]"; }
    }

    public record Resultado(String nombre, String descripcion, BigDecimal precio, String codigoMoneda,
                            String imagen, String fuente, String enlace) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Respuesta(Estado estado, List<Resultado> resultados, Sesion sesion) {
        @Override public String toString() { return "RespuestaLector[estado=" + estado + "]"; }
    }
}
