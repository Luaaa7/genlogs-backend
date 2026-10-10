package com.genlogs.app.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contrato exacto esperado por el frontend en
 * src/types/dashboard.types.ts -> FacturacionHistoricoItem.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacturacionHistoricoItemResponse {
    private String mes;
    private BigDecimal monto;
    private BigDecimal comparativaAnoAnterior;
}
