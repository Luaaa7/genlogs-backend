package com.genlogs.app.util;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.TipoDocumento;

/**
 * Reglas comunes de clientes y proveedores sobre los datos del tercero:
 * validación del número de documento y armado del nombre.
 *
 * Con RUC se guarda la razón social. Con DNI o pasaporte se guardan los
 * nombres, el apellido paterno y el apellido materno por separado, y la
 * razón social queda como el nombre completo para los listados, las
 * cotizaciones y los PDF que ya la usan.
 */
public final class DatosTercero {

    /** Mínimo de caracteres para los documentos con letras (pasaporte). */
    private static final int MINIMO_ALFANUMERICO = 6;

    private DatosTercero() {
    }

    /** Misma regla que el trigger fn_tercero_valida_documento() de la base de datos. */
    public static void validarDocumento(TipoDocumento tipo, String numero) {
        Number longitud = tipo.getLongitudDocumento();
        if (longitud == null || longitud.intValue() <= 0) {
            throw new BusinessException("El tipo de documento tiene una longitud configurada inválida");
        }
        int maximo = longitud.intValue();
        String codigo = tipo.getCodigoTipo();

        if (Boolean.TRUE.equals(tipo.getSoloNumerico())) {
            if (!numero.matches("^[0-9]+$")) {
                throw new BusinessException("El " + codigo + " debe tener solo números");
            }
            if (numero.length() != maximo) {
                throw new BusinessException(String.format("El %s debe tener %d dígitos", codigo, maximo));
            }
            if ("RUC".equalsIgnoreCase(codigo) && !numero.matches("^(10|20)[0-9]{9}$")) {
                throw new BusinessException("El RUC debe empezar con 10 o 20");
            }
            return;
        }

        if (!numero.matches("^[A-Za-z0-9]+$")
                || numero.length() < MINIMO_ALFANUMERICO || numero.length() > maximo) {
            throw new BusinessException(String.format(
                    "El %s debe tener de %d a %d letras o números",
                    "PAS".equalsIgnoreCase(codigo) ? "pasaporte" : codigo, MINIMO_ALFANUMERICO, maximo));
        }
    }

    /** true para RUC: se registra con razón social y no con nombres y apellidos. */
    public static boolean esEmpresa(TipoDocumento tipo) {
        return "RUC".equalsIgnoreCase(tipo.getCodigoTipo());
    }

    /**
     * Copia al tercero la razón social (RUC) o los nombres y apellidos
     * (DNI y pasaporte). En el segundo caso la razón social pasa a ser el
     * nombre completo.
     */
    public static void aplicarNombre(Tercero tercero, TipoDocumento tipo, String razonSocial,
                                     String nombres, String apellidoPaterno, String apellidoMaterno) {
        if (esEmpresa(tipo)) {
            String razon = limpio(razonSocial);
            if (razon == null) {
                throw new BusinessException("Ingresa la razón social");
            }
            tercero.setRazonSocial(razon);
            tercero.setNombres(null);
            tercero.setApellidoPaterno(null);
            tercero.setApellidoMaterno(null);
            return;
        }

        String nom = limpio(nombres);
        String pat = limpio(apellidoPaterno);
        String mat = limpio(apellidoMaterno);

        if (nom == null && pat == null) {
            // Datos de antes (solo razón social): se respetan tal cual.
            String razon = limpio(razonSocial);
            if (razon == null) {
                throw new BusinessException("Ingresa los nombres y el apellido paterno");
            }
            tercero.setRazonSocial(razon);
            return;
        }
        if (nom == null) {
            throw new BusinessException("Ingresa los nombres");
        }
                if (pat == null) {
            throw new BusinessException("Ingresa el apellido paterno");
        }
        if (mat == null) {
            throw new BusinessException("Ingresa el apellido materno");
        }

        tercero.setNombres(nom);
        tercero.setApellidoPaterno(pat);
        tercero.setApellidoMaterno(mat);
        tercero.setRazonSocial(String.join(" ", nom, pat, mat));
    }

    private static String limpio(String valor) {
        if (valor == null) {
            return null;
        }
        String v = valor.trim().replaceAll("\\s+", " ");
        return v.isEmpty() ? null : v;
    }
}