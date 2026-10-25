package com.genlogs.app.service.pdf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import com.genlogs.app.config.EmisorProperties;
import com.genlogs.app.service.pdf.PdfGenerationService.CotizacionPdfData;
import com.genlogs.app.service.pdf.PdfGenerationService.FacturacionPdfData;

class PdfGenerationServiceTest {

    private final PdfGenerationService servicio =
            new PdfGenerationService(new EmisorProperties("General Logistic Solutions", "20516185644", "Av. Ejemplo 123, Lima"));

    private String textoDe(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void facturaIncluyeEmisorClienteFormaDePagoYFormatoNumerico() throws IOException {
        List<String[]> lineas = new ArrayList<>();
        lineas.add(new String[] { "Programación y puesta en marcha del controlador y sus interfaces en planta", "18", "233.85", "4,209.30" });
        FacturacionPdfData data = new FacturacionPdfData(
                "Factura", "F001-00000002", "ANTAMINA S.A.",
                "RUC", "20100000001", "Av. Minera 456, Lima",
                LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 27),
                "Crédito 30 días", "USD",
                lineas,
                new BigDecimal("3567.20"), new BigDecimal("642.10"), new BigDecimal("4209.30"), BigDecimal.ZERO);

        String texto = textoDe(servicio.generarPdfFacturacion(data));

        assertTrue(texto.contains("General Logistic Solutions"));
        assertTrue(texto.contains("20516185644"));
        assertTrue(texto.contains("F001-00000002"));
        assertTrue(texto.contains("20100000001"));
        assertTrue(texto.contains("Av. Minera 456"));
        assertTrue(texto.contains("Crédito 30 días"));
        assertTrue(texto.contains("USD 4,209.30"));
        assertTrue(texto.contains("Saldo pendiente"));
    }

    @Test
    void cotizacionMuestraDescripcionCompletaCantidadesEnterasYMontoConMoneda() throws IOException {
        List<String[]> lineas = new ArrayList<>();
        lineas.add(new String[] { "Programación y puesta en marcha del controlador y sus interfaces en planta", "4", "3,254.50", "13,018.00" });
        CotizacionPdfData data = new CotizacionPdfData(
                "20261012-01ADM", "ANTAMINA S.A.", "RUC", "20100000001", "Av. Minera 456, Lima",
                LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 27), "Contado", "USD",
                lineas,
                new BigDecimal("13018.00"), new BigDecimal("2343.24"), new BigDecimal("15361.24"));

        String texto = textoDe(servicio.generarPdfCotizacion(data)).replaceAll("\\s+", " ");

        assertTrue(texto.contains("COTIZACIÓN"));
        assertTrue(texto.contains("20261012-01ADM"));
        assertTrue(texto.contains("20516185644"));
        assertTrue(texto.contains("Contado"));
        assertTrue(texto.contains("Programación") && texto.contains("planta"));
        assertTrue(texto.contains("USD 13,018.00"));
        assertTrue(texto.contains("USD 15,361.24"));
    }

    @Test
    void facturaConMuchasLineasPasaAOtraPaginaSinFallar() throws IOException {
        List<String[]> lineas = new java.util.ArrayList<>();
        for (int i = 0; i < 80; i++) {
            lineas.add(new String[] { "Ítem " + i + " con una descripción bastante larga para ocupar varias líneas en la tabla", "1", "10.00", "10.00" });
        }
        FacturacionPdfData data = new FacturacionPdfData(
                "Factura", "F001-00000009", "Cliente", null, null, null,
                LocalDate.now(), LocalDate.now(), null, "PEN", lineas,
                new BigDecimal("800.00"), new BigDecimal("144.00"), new BigDecimal("944.00"), BigDecimal.ZERO);

        byte[] pdf = servicio.generarPdfFacturacion(data);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertTrue(doc.getNumberOfPages() > 1);
        }
    }
}
