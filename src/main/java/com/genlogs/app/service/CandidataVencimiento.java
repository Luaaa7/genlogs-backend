package com.genlogs.app.service;

import java.time.LocalDate;

/** Cotización que cumple la regla de vencimiento. Sin datos del cliente. */
public record CandidataVencimiento(Long idCotizacion, String codigoCotizacion, LocalDate fechaCotizacion) {
}
