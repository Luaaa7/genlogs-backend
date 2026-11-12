package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

class LectorOcPdfTest {

    private final LectorOcPdf lector = new LectorOcPdf();

    @Test
    void leeElNumeroDeOchoADoceDigitos() throws Exception {
        LecturaOc lectura = lector.leer(pdf("Orden de Compra Nro 4500012345"));

        assertEquals("4500012345", lectura.numeroOc());
    }

    @Test
    void leeMontosConComaOPuntoYConMiles() throws Exception {
        assertEquals(new BigDecimal("1234.56"), monto("Total 1234,56"));
        assertEquals(new BigDecimal("1234.56"), monto("Total 1234.56"));
        assertEquals(new BigDecimal("1234.56"), monto("Total 1.234,56"));
        assertEquals(new BigDecimal("1234.56"), monto("Importe total 1,234.56"));
    }

    @Test
    void leeLaFechaEnDosFormatosYRechazaLaInvalida() throws Exception {
        assertEquals(LocalDate.of(2026, 3, 15), lector.leer(pdf("Fecha 15/03/2026")).fechaEmision());
        assertEquals(LocalDate.of(2026, 3, 15), lector.leer(pdf("Fecha 15-03-2026")).fechaEmision());
        assertEquals(LocalDate.of(2026, 3, 15),
                lector.leer(pdf("Fecha 15 de marzo de 2026")).fechaEmision());

        LecturaOc invalida = lector.leer(pdf("Orden de Compra 4500012345", "Fecha 31/02/2026"));
        assertEquals("4500012345", invalida.numeroOc());
        assertNull(invalida.fechaEmision());
    }

    @Test
    void eligeElMontoDelTotal() throws Exception {
        LecturaOc lectura = lector.leer(pdf(
                "Orden de Compra 4500012345",
                "Subtotal 100.00",
                "IGV 18.00",
                "Total PEN 1,500.50"));

        assertEquals(new BigDecimal("1500.50"), lectura.monto());
        assertEquals("PEN", lectura.moneda());
        assertEquals("PEN", lector.leer(pdf("Total S/ 10.00")).moneda());
    }

    @Test
    void noLeeMasAllaDeLaQuintaPagina() throws Exception {
        LecturaOc lectura = lector.leer(pdfConNumeroEnLaPagina6());

        assertNull(lectura.numeroOc());
    }

    @Test
    void vacioSinTextoYBasuraNoLanzan() {
        assertTrue(lector.leer(new byte[0]).equals(LecturaOc.vacia()) || !lector.leer(new byte[0]).hayAlgo());
        assertNull(lector.leer(new byte[0]).numeroOc());
        assertNull(lector.leer(pdfVacio()).numeroOc());
        assertNull(lector.leer(pdfVacio()).monto());
        assertNull(lector.leer(new byte[] {1, 2, 3, 4, 9}).numeroOc());
        assertNull(lector.leer(new byte[] {1, 2, 3, 4, 9}).monto());
    }

    private BigDecimal monto(String linea) throws Exception {
        return lector.leer(pdf(linea)).monto();
    }

    static byte[] pdf(String... lineas) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document documento = new Document();
        PdfWriter.getInstance(documento, out);
        documento.open();
        for (String linea : lineas) {
            documento.add(new Paragraph(linea));
        }
        documento.close();
        return out.toByteArray();
    }

    private static byte[] pdfVacio() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document documento = new Document();
            PdfWriter.getInstance(documento, out);
            documento.open();
            documento.add(new Paragraph(" "));
            documento.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] pdfConNumeroEnLaPagina6() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document documento = new Document();
        PdfWriter.getInstance(documento, out);
        documento.open();
        for (int i = 0; i < 5; i++) {
            documento.add(new Paragraph("Pagina sin orden"));
            documento.newPage();
        }
        documento.add(new Paragraph("Orden de Compra 4500012345"));
        documento.close();
        return out.toByteArray();
    }
}
