package com.genlogs.app.util;

import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PdfUtilsTest {

    @Test
    void sanitizarTextoPdf_nullReturnsEmpty() {
        assertEquals("", PdfUtils.sanitizarTextoPdf(null));
    }

    @Test
    void sanitizarTextoPdf_replacesControlChars() {
        String input = "Linea1\r\nLinea2\tTab\u2014dash\u2013en\u2022bullet\u201cquote\u201d\u2018single\u2019";
        // \r\n -> two spaces, \t -> space, em dash -> '-', en dash -> '-', bullet -> '*', quotes replaced
        String expected = "Linea1  Linea2 Tab-dash-en*bullet\"quote\"'single'";
        assertEquals(expected, PdfUtils.sanitizarTextoPdf(input));
    }

    @Test
    void ajustarTexto_nullOrBlankReturnsEmpty() {
        assertEquals("", PdfUtils.ajustarTexto(null, null, 10, 100));
        assertEquals("", PdfUtils.ajustarTexto("", null, 10, 100));
        assertEquals("", PdfUtils.ajustarTexto("   ", null, 10, 100));
    }

    @Test
    void ajustarTexto_truncatesWhenTooLong() {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        // Very long text, small max width forces truncation
        String longText = "Este es un texto muy largo que debería ser truncado con puntos suspensivos";
        String result = PdfUtils.ajustarTexto(longText, font, 12, 50); // width small
        assertTrue(result.endsWith("..."));
        assertTrue(result.length() <= longText.length());
    }
}