package com.genlogs.app.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.ParametroSistema;
import com.genlogs.app.repository.ParametroSistemaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Lectura de parametro_sistema (Paso 0, solo lectura). Lo usan todos los bloques:
 *
 * <pre>
 *   int dias = parametrosService.getEntero(ClavesParametro.PLAZO_COMUN_DIAS);
 *   BigDecimal monto = parametrosService.getDecimal(ClavesParametro.MONTO_ALTO_VALOR_PEN);
 * </pre>
 *
 * La edicion (solo JEFE_ADMINISTRATIVO, RF-87) es de BE-D y va en su propio servicio.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParametrosService {

    private final ParametroSistemaRepository parametroSistemaRepository;

    /** Valor del parametro. Si no existe, error claro (falta correr v3_2.sql o el v3_2_be_x.sql del bloque). */
    public String get(String clave) {
        return buscar(clave).orElseThrow(() -> new BusinessException(
                "Falta el parámetro " + clave + " en parametro_sistema. Revisa que se hayan corrido las migraciones v3.2."));
    }

    /** Valor del parametro, o vacio si no existe. */
    public Optional<String> buscar(String clave) {
        return parametroSistemaRepository.findById(clave)
                .map(ParametroSistema::getValor)
                .map(String::trim);
    }

    public int getEntero(String clave) {
        String valor = get(clave);
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw new BusinessException("El parámetro " + clave + " debe ser un número entero y vale \"" + valor + "\".");
        }
    }

    public BigDecimal getDecimal(String clave) {
        String valor = get(clave);
        try {
            return new BigDecimal(valor);
        } catch (NumberFormatException e) {
            throw new BusinessException("El parámetro " + clave + " debe ser un número y vale \"" + valor + "\".");
        }
    }
}