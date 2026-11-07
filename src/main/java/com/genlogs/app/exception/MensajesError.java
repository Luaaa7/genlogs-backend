package com.genlogs.app.exception;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traduce errores técnicos (base de datos, JSON, parámetros) a mensajes claros
 * en español para el usuario final. El detalle técnico se queda en el log.
 */
public final class MensajesError {

    private static final Pattern CLAVE = Pattern.compile("Key \\(([^)]+)\\)=\\(([^)]*)\\)");
    private static final Pattern TABLA = Pattern.compile("table \"([^\"]+)\"");
    private static final Pattern COLUMNA_NULA = Pattern.compile("column \"([^\"]+)\"");
    private static final Pattern RESTRICCION = Pattern.compile("constraint \"([^\"]+)\"");
    private static final Pattern CAMPO_JSON = Pattern.compile("\\[\"([^\"]+)\"\\]");
    private static final Pattern VALORES_ENUM = Pattern.compile("Enum class: \\[([^\\]]*)\\]");
    private static final Pattern VALOR_STRING = Pattern.compile("from String \"([^\"]*)\"");

    private MensajesError() {
    }

    // ------------------------------------------------------------------ base de datos

    /** Mensaje para una DataIntegrityViolationException (duplicado, referencia, nulo, check). */
    public static String integridad(Throwable ex) {
        SQLException sql = buscar(ex, SQLException.class);
        String estado = sql == null ? null : sql.getSQLState();
        String texto = textoCompleto(ex);

        if ("23505".equals(estado) || contiene(texto, "duplicate key", "unique index", "unique constraint")) {
            Matcher m = CLAVE.matcher(texto);
            if (m.find()) {
                return "Ya existe un registro con " + campoLegible(m.group(1)) + " \"" + m.group(2)
                        + "\". Usa un valor diferente.";
            }
            return "Ya existe un registro con esos datos. Revisa que no esté duplicado.";
        }
        if ("23503".equals(estado) || contiene(texto, "foreign key", "referential integrity")) {
            if (contiene(texto, "still referenced", "referenced from")) {
                Matcher t = TABLA.matcher(texto.substring(texto.indexOf("referenced")));
                String donde = t.find() ? " (" + campoLegible(t.group(1)) + ")" : "";
                return "No se puede eliminar porque otros registros dependen de él" + donde
                        + ". Puedes desactivarlo en su lugar.";
            }
            Matcher m = CLAVE.matcher(texto);
            if (m.find()) {
                return "El " + campoLegible(m.group(1)) + " indicado no existe o fue eliminado.";
            }
            return "Uno de los datos relacionados no existe o fue eliminado.";
        }
        if ("23502".equals(estado) || contiene(texto, "not-null", "null value", "NULL not allowed")) {
            Matcher m = COLUMNA_NULA.matcher(texto);
            return m.find()
                    ? "Falta un dato obligatorio: " + campoLegible(m.group(1)) + "."
                    : "Falta completar un dato obligatorio.";
        }
        if ("23514".equals(estado) || contiene(texto, "check constraint")) {
            Matcher m = RESTRICCION.matcher(texto);
            return m.find()
                    ? "Uno de los valores no es válido (regla " + m.group(1) + ")."
                    : "Uno de los valores no cumple las reglas permitidas.";
        }
        if ("22001".equals(estado) || contiene(texto, "value too long")) {
            return "Uno de los textos es demasiado largo para guardarse.";
        }
        return "No se pudo guardar porque los datos entran en conflicto con otros registros.";
    }

    // ------------------------------------------------------------------ JSON / parámetros

    /** Mensaje para un cuerpo JSON que no se pudo leer (tipo equivocado, enum inválido, fecha mal escrita). */
    public static String cuerpoIlegible(Throwable ex) {
        String texto = textoCompleto(ex);
        if (texto.isBlank() || contiene(texto, "Required request body is missing")) {
            return "La petición no trae datos. Completa el formulario e inténtalo de nuevo.";
        }

        String campo = ultimo(CAMPO_JSON, texto);
        String enCampo = campo == null ? "" : " en el campo \"" + campo + "\"";

        Matcher enumM = VALORES_ENUM.matcher(texto);
        if (enumM.find()) {
            String valor = ultimo(VALOR_STRING, texto);
            return "Valor no válido" + enCampo + (valor == null ? "" : ": \"" + valor + "\"")
                    + ". Valores permitidos: " + enumM.group(1) + ".";
        }
        if (contiene(texto, "LocalDate", "LocalDateTime", "java.time")) {
            return "La fecha" + enCampo + " no tiene un formato válido (usa AAAA-MM-DD).";
        }
        if (contiene(texto, "java.lang.Long", "java.lang.Integer", "BigDecimal", "java.lang.Double",
                "Short", "not a valid representation", "not a valid `int`", "not a valid `long`")) {
            return "Se esperaba un número" + enCampo + ".";
        }
        if (contiene(texto, "java.lang.Boolean", "`boolean`")) {
            return "Se esperaba sí/no (true o false)" + enCampo + ".";
        }
        if (contiene(texto, "JSON parse error", "Unexpected character", "Unrecognized token", "Unexpected end")) {
            return "Los datos enviados no tienen un formato válido.";
        }
        return "Los datos enviados no son válidos" + enCampo + ".";
    }

    /** Mensaje para un parámetro de URL con tipo equivocado (?estado=XYZ, /reclamos/abc). */
    public static String parametroInvalido(String nombre, Object valor, Class<?> tipo) {
        String base = "El valor \"" + valor + "\" no es válido para \"" + nombre + "\"";
        if (tipo != null && tipo.isEnum()) {
            StringBuilder permitidos = new StringBuilder();
            for (Object c : tipo.getEnumConstants()) {
                if (permitidos.length() > 0) permitidos.append(", ");
                permitidos.append(((Enum<?>) c).name());
            }
            return base + ". Valores permitidos: " + permitidos + ".";
        }
        if (tipo != null && (Number.class.isAssignableFrom(tipo) || tipo.isPrimitive())) {
            return base + ": debe ser un número.";
        }
        if (tipo != null && tipo.getName().startsWith("java.time")) {
            return base + ": usa el formato de fecha AAAA-MM-DD.";
        }
        return base + ".";
    }

    // ------------------------------------------------------------------ helpers

    /** "id_cliente" -> "cliente", "numero_documento" -> "número de documento". */
    static String campoLegible(String columna) {
        String c = columna.trim().replace("\"", "");
        if (c.contains(",")) {
            c = c.split(",")[0].trim();
        }
        c = c.toLowerCase().replaceFirst("^id_", "").replace('_', ' ');
        return c.replace("numero ", "número de ")
                .replace("codigo ", "código de ")
                .replace("razon social", "razón social")
                .trim();
    }

    private static <T extends Throwable> T buscar(Throwable ex, Class<T> tipo) {
        Throwable t = ex;
        int vueltas = 0;
        while (t != null && vueltas++ < 15) {
            if (tipo.isInstance(t)) return tipo.cast(t);
            t = t.getCause();
        }
        return null;
    }

    private static String textoCompleto(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        Throwable t = ex;
        int vueltas = 0;
        while (t != null && vueltas++ < 15) {
            if (t.getMessage() != null) sb.append(t.getMessage()).append(' ');
            t = t.getCause();
        }
        return sb.toString();
    }

    private static boolean contiene(String texto, String... partes) {
        for (String p : partes) {
            if (texto.contains(p)) return true;
        }
        return false;
    }

    private static String ultimo(Pattern p, String texto) {
        Matcher m = p.matcher(texto);
        String ultimo = null;
        while (m.find()) ultimo = m.group(1);
        return ultimo;
    }
}
