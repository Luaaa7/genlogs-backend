package com.genlogs.app.service;

import com.genlogs.app.dto.ReporteRequest;
import com.genlogs.app.model.Cotizacion;
import com.genlogs.app.model.Facturacion;
import com.genlogs.app.model.OrdenCompra;
import com.genlogs.app.model.Producto;
import com.genlogs.app.model.Servicio;
import com.genlogs.app.repository.CotizacionRepository;
import com.genlogs.app.repository.FacturacionRepository;
import com.genlogs.app.repository.OrdenCompraRepository;
import com.genlogs.app.repository.ProductoRepository;
import com.genlogs.app.repository.ServicioRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReporteService {

    private final ServicioRepository servicioRepository;
    private final CotizacionRepository cotizacionRepository;
    private final OrdenCompraRepository ordenCompraRepository;
    private final FacturacionRepository facturacionRepository;
    private final ProductoRepository productoRepository;

    public byte[] generarReporte(ReporteRequest request) throws IOException {
        LocalDateTime inicio = request.getFechaInicio().atStartOfDay();
        LocalDateTime fin = request.getFechaFin().atTime(23, 59, 59);

        if (request.getFormato() != ReporteRequest.FormatoReporte.EXCEL) {
            throw new UnsupportedOperationException("El formato PDF todavía no está implementado");
        }

        switch (request.getTipoReporte()) {
            case SERVICIOS:
                return excelServicios(servicioRepository.findByDateCreateBetween(inicio, fin));
            case PRODUCTOS:
                return excelProductos(productoRepository.findByDateCreateBetween(inicio, fin));
            case COTIZACIONES:
                return excelCotizaciones(cotizacionRepository.findByDateCreateBetween(inicio, fin));
            case ORDENES_COMPRA:
                return excelOrdenesCompra(ordenCompraRepository.findByDateCreateBetween(inicio, fin));
            case FACTURACION:
                return excelFacturacion(facturacionRepository.findByDateCreateBetween(inicio, fin));
            default:
                throw new UnsupportedOperationException(
                    "El reporte de " + request.getTipoReporte() + " todavía no está implementado");
        }
    }

    // ---------- SERVICIOS ----------
    private byte[] excelServicios(List<Servicio> servicios) throws IOException {
        return construirExcel("Servicios",
            new String[]{ "Código", "Nombre", "Categoría", "Unidad", "Duración (h)", "Visible Web", "Estado" },
            servicios,
            (row, s) -> {
                row.createCell(0).setCellValue(s.getCodigoServicio());
                row.createCell(1).setCellValue(s.getNombreServicio());
                row.createCell(2).setCellValue(s.getCategoriaServicio() != null ? s.getCategoriaServicio().getNombreCategoria() : "");
                row.createCell(3).setCellValue(s.getUnidadMedida() != null ? s.getUnidadMedida().getNombreUnidad() : "");
                row.createCell(4).setCellValue(s.getDuracionEstimadaHoras() != null ? s.getDuracionEstimadaHoras().doubleValue() : 0);
                row.createCell(5).setCellValue(Boolean.TRUE.equals(s.getVisibleWeb()) ? "Sí" : "No");
                row.createCell(6).setCellValue("A".equals(s.getStatus()) ? "Activo" : "Inactivo");
            });
    }

    // ---------- PRODUCTOS ----------
    private byte[] excelProductos(List<Producto> productos) throws IOException {
        return construirExcel("Productos",
            new String[]{ "Código", "Nombre", "Categoría", "Marca", "Unidad", "Visible Web", "Estado" },
            productos,
            (row, p) -> {
                row.createCell(0).setCellValue(p.getCodigoProducto());
                row.createCell(1).setCellValue(p.getNombreProducto());
                row.createCell(2).setCellValue(p.getCategoriaProducto() != null ? p.getCategoriaProducto().getNombreCategoria() : "");
                row.createCell(3).setCellValue(p.getMarca() != null ? p.getMarca().getNombreMarca() : "");
                row.createCell(4).setCellValue(p.getUnidadMedida() != null ? p.getUnidadMedida().getNombreUnidad() : "");
                row.createCell(5).setCellValue(Boolean.TRUE.equals(p.getVisibleWeb()) ? "Sí" : "No");
                row.createCell(6).setCellValue("A".equals(p.getStatus()) ? "Activo" : "Inactivo");
            });
    }

    // ---------- COTIZACIONES ----------
    private byte[] excelCotizaciones(List<Cotizacion> cotizaciones) throws IOException {
        return construirExcel("Cotizaciones",
            new String[]{ "Código", "Cliente", "Fecha", "Validez", "Estado", "Moneda" },
            cotizaciones,
            (row, c) -> {
                row.createCell(0).setCellValue(c.getCodigoCotizacion());
                row.createCell(1).setCellValue(c.getCliente() != null && c.getCliente().getTercero() != null
                    ? c.getCliente().getTercero().getRazonSocial() : "");
                row.createCell(2).setCellValue(c.getFechaCotizacion() != null ? c.getFechaCotizacion().toString() : "");
                row.createCell(3).setCellValue(c.getFechaValidez() != null ? c.getFechaValidez().toString() : "");
                row.createCell(4).setCellValue(c.getEstadoCotizacion() != null ? c.getEstadoCotizacion().getNombreEstado() : "");
                row.createCell(5).setCellValue(c.getMoneda() != null ? c.getMoneda().getCodigoMoneda() : "");
            });
    }

    // ---------- ÓRDENES DE COMPRA ----------
    private byte[] excelOrdenesCompra(List<OrdenCompra> ordenes) throws IOException {
        return construirExcel("Ordenes de Compra",
            new String[]{ "Número O/C", "Cotización", "Fecha Recepción", "Estado" },
            ordenes,
            (row, oc) -> {
                row.createCell(0).setCellValue(oc.getNumeroOrdenCompra());
                row.createCell(1).setCellValue(oc.getCotizacion() != null ? oc.getCotizacion().getCodigoCotizacion() : "");
                row.createCell(2).setCellValue(oc.getFechaRecepcion() != null ? oc.getFechaRecepcion().toString() : "");
                row.createCell(3).setCellValue(oc.getEstadoOrdenCompra() != null ? oc.getEstadoOrdenCompra().getNombreEstado() : "");
            });
    }

    // ---------- FACTURACIÓN ----------
    private byte[] excelFacturacion(List<Facturacion> facturas) throws IOException {
        return construirExcel("Facturacion",
            new String[]{ "Comprobante", "Fecha Emisión", "Vencimiento", "Monto Pagado", "Estado" },
            facturas,
            (row, f) -> {
                                row.createCell(0).setCellValue(f.getSerieComprobante() + "-" + f.getNumeroComprobante());
                row.createCell(1).setCellValue(f.getFechaEmision() != null ? f.getFechaEmision().toString() : "");
                row.createCell(2).setCellValue(f.getFechaVencimiento() != null ? f.getFechaVencimiento().toString() : "");
                row.createCell(3).setCellValue(f.getMontoPagado() != null ? f.getMontoPagado().doubleValue() : 0);
                row.createCell(4).setCellValue(f.getEstadoFacturacion() != null ? f.getEstadoFacturacion().getNombreEstado() : "");
            });
    }

    // ---------- Helper genérico para no repetir la construcción del Excel ----------
    private <T> byte[] construirExcel(String nombreHoja, String[] columnas, List<T> registros,
                                       FilaEscritor<T> escritor) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(nombreHoja);

            Row header = sheet.createRow(0);
            for (int i = 0; i < columnas.length; i++) {
                header.createCell(i).setCellValue(columnas[i]);
            }

            int filaIndex = 1;
            for (T registro : registros) {
                Row row = sheet.createRow(filaIndex++);
                escritor.escribir(row, registro);
            }

            for (int i = 0; i < columnas.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @FunctionalInterface
    private interface FilaEscritor<T> {
        void escribir(Row row, T registro);
    }
}