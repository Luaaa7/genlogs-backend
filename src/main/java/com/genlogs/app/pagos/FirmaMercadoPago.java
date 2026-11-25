package com.genlogs.app.pagos;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Valida el encabezado x-signature del aviso de Mercado Pago:
 * HMAC-SHA256 (hex) de "id:{data.id};request-id:{x-request-id};ts:{ts};" con la clave secreta
 * de la aplicación. Las partes que no vienen se omiten.
 */
public final class FirmaMercadoPago {

    private FirmaMercadoPago() {
    }

    public static boolean valida(String xSignature, String xRequestId, String dataId, String secreto) {
        if (vacio(xSignature) || vacio(secreto)) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String parte : xSignature.split(",")) {
            String[] kv = parte.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String clave = kv[0].trim();
            if ("ts".equals(clave)) {
                ts = kv[1].trim();
            } else if ("v1".equals(clave)) {
                v1 = kv[1].trim();
            }
        }
        if (vacio(ts) || vacio(v1)) {
            return false;
        }
        String esperado = hmacHex(manifiesto(dataId, xRequestId, ts), secreto);
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    static String manifiesto(String dataId, String xRequestId, String ts) {
        StringBuilder m = new StringBuilder();
        if (!vacio(dataId)) {
            // Mercado Pago pide el id en minúsculas si es alfanumérico.
            m.append("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
        }
        if (!vacio(xRequestId)) {
            m.append("request-id:").append(xRequestId).append(';');
        }
        m.append("ts:").append(ts).append(';');
        return m.toString();
    }

    static String hmacHex(String texto, String secreto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular la firma", e);
        }
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}