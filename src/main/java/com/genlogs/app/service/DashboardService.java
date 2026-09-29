package com.genlogs.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import org.springframework.stereotype.Service;

import com.genlogs.app.dto.CotizacionEstadoItemResponse;
import com.genlogs.app.dto.DashboardIndicadoresResponse;
import com.genlogs.app.dto.FacturacionHistoricoItemResponse;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.CotizacionRepository.EstadoCantidadProjection;
import com.genlogs.app.repository.FacturacionRepository;
import com.genlogs.app.repository.FacturacionRepository.PeriodoMontoProjection;
import com.genlogs.app.repository.OrdenCompraRepository;

import lombok.RequiredArgsConstructor;

/**
 * Agrega datos de Cotizacion, Facturacion, Cliente y OrdenCompra para
 * alimentar el Dashboard del frontend (src/types/dashboard.types.ts).
 *
 * Ni "cotizacion" ni "facturacion" guardan un total en la tabla: los montos
 * se calculan siempre contra las vistas vw_cotizacion_totales /
 * vw_facturacion_totales (ver CotizacionRepository/FacturacionRepository).
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CotizacionRepository cotizacionRepository;
    private final FacturacionRepository facturacionRepository;
    private final ClienteRepository clienteRepository;
    private final OrdenCompraRepository ordenCompraRepository;

    public DashboardIndicadoresResponse obtenerIndicadores(LocalDate desde, LocalDate hasta) {
        LocalDate hoy = LocalDate.now();

        // Sin rango explícito: se usa el mes en curso, más útil como KPI que el
        // acumulado histórico y coherente con la variación % vs. mes anterior.
        LocalDate desdeEfectivo = desde;
        LocalDate hastaEfectivo = hasta;
        if (desdeEfectivo == null && hastaEfectivo == null) {
            desdeEfectivo = hoy.with(TemporalAdjusters.firstDayOfMonth());
            hastaEfectivo = hoy;
        }

        BigDecimal totalFacturado = facturacionRepository.sumTotalFacturado(desdeEfectivo, hastaEfectivo);

        BigDecimal totalMesActual = facturacionRepository.sumTotalFacturadoDelMes(hoy);
        BigDecimal totalMesAnterior = facturacionRepository.sumTotalFacturadoDelMes(hoy.minusMonths(1));
        double variacion = calcularVariacionPorcentual(totalMesAnterior, totalMesActual);

        long cotizacionesPendientes = cotizacionRepository.countPendientes();
        long clientesActivos = clienteRepository.countBySituacionAndStatus("ACTIVO", "A");
        long ordenesCompraProceso = ordenCompraRepository.countEnProceso();

        return DashboardIndicadoresResponse.builder()
                .totalFacturado(totalFacturado == null ? BigDecimal.ZERO : totalFacturado)
                .cotizacionesPendientes(cotizacionesPendientes)
                .clientesActivos(clientesActivos)
                .ordenesCompraProceso(ordenesCompraProceso)
                .porcentajeVariacionFacturacion(variacion)
                .build();
    }

    public List<FacturacionHistoricoItemResponse> obtenerHistoricoFacturacion(String periodo) {
        LocalDate hoy = LocalDate.now();
        String periodoNormalizado = periodo == null ? "mensual" : periodo.trim().toLowerCase();

        List<PeriodoMontoProjection> filas;
        switch (periodoNormalizado) {
            case "trimestral":
                filas = facturacionRepository.sumTrimestralDesde(hoy.minusMonths(24));
                break;
            case "anual":
                filas = facturacionRepository.sumAnualDesde(hoy.minusYears(5));
                break;
            case "mensual":
            default:
                filas = facturacionRepository.sumMensualDesde(hoy.minusMonths(11));
                break;
        }

        return filas.stream()
                .map(f -> FacturacionHistoricoItemResponse.builder()
                        .mes(f.getPeriodo())
                        .monto(f.getMonto() == null ? BigDecimal.ZERO : f.getMonto())
                        .comparativaAnoAnterior(null)
                        .build())
                .toList();
    }

    public List<CotizacionEstadoItemResponse> obtenerCotizacionesPorEstado() {
        List<EstadoCantidadProjection> filas = cotizacionRepository.countAgrupadoPorEstado();
        long total = filas.stream().mapToLong(EstadoCantidadProjection::getCantidad).sum();

        return filas.stream()
                .map(f -> CotizacionEstadoItemResponse.builder()
                        .estado(f.getEstado())
                        .cantidad(f.getCantidad())
                        .porcentaje(total == 0 ? 0.0 : redondear(f.getCantidad() * 100.0 / total))
                        .build())
                .toList();
    }

    private double calcularVariacionPorcentual(BigDecimal anterior, BigDecimal actual) {
        BigDecimal anteriorSeguro = anterior == null ? BigDecimal.ZERO : anterior;
        BigDecimal actualSeguro = actual == null ? BigDecimal.ZERO : actual;

        if (anteriorSeguro.compareTo(BigDecimal.ZERO) == 0) {
            return actualSeguro.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
        }

        return redondear(actualSeguro.subtract(anteriorSeguro)
                .divide(anteriorSeguro, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue());
    }

    private double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
