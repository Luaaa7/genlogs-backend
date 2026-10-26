package com.genlogs.app.service.pdf;

import com.genlogs.app.model.Producto;
import com.genlogs.app.model.Servicio;
import com.genlogs.app.model.UnidadMedida;
import com.genlogs.app.repository.ProductoImagenRepository;
import com.genlogs.app.repository.ServicioImagenRepository;
import com.genlogs.app.service.TasaIgvService;
import com.genlogs.app.util.PdfUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Genera los PDF de GENLOGS (cotización, factura y reportes) con el formato
 * de las cotizaciones que la empresa arma en Word:
 * logo arriba en cada página, título centrado y subrayado, "Señores / RUC",
 * tabla con bordes (ITEM, CANT, U/M, DESCRIPCIÓN, P. UNITARIO, TOTAL),
 * "TOTAL, COTIZACIÓN … MÁS 18% I.G.V." (con el % de cada documento), condiciones, observaciones,
 * "Atentamente," y la dirección y correos de la empresa al pie de cada página.
 */
@Service
public class PdfGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PdfGenerationService.class);

    // ── Página ───────────────────────────────────────────────────────────
    private static final float ANCHO_PAGINA = PDRectangle.A4.getWidth();
    private static final float ALTO_PAGINA = PDRectangle.A4.getHeight();
    private static final float MARGEN = 60f;
    private static final float BORDE_DER = ANCHO_PAGINA - MARGEN;
    private static final float ANCHO_UTIL = ANCHO_PAGINA - 2 * MARGEN;
    private static final float LOGO_ANCHO = 160f;
    /** Donde empieza el contenido, debajo del logo. */
    private static final float Y_CONTENIDO = ALTO_PAGINA - 115f;
    /** Límite inferior del contenido, encima del pie. */
    private static final float Y_MINIMO = 80f;

    // ── Tabla de ítems: ITEM | CANT | U/M | DESCRIPCIÓN | P. UNITARIO | TOTAL ──
    private static final String[] ENCABEZADOS_ITEMS = { "ITEM", "CANT", "U/M", "DESCRIPCIÓN", "P. UNITARIO", "TOTAL" };
    private static final float[] ANCHOS_ITEMS = { 34f, 36f, 36f, 227f, 70f, 72f };
    private static final float TAM_TABLA = 8f;
    private static final float INTERLINEA_TABLA = 10.5f;
    private static final float RELLENO = 4f;
    private static final float TAM_ENCABEZADO = 7.5f;

    private static final Color NEGRO = Color.BLACK;
    private static final Color GRIS_TEXTO = new Color(90, 90, 90);
    private static final Color GRIS_FONDO = new Color(232, 235, 239);
    private static final Pattern NUMERICO = Pattern.compile("^-?[\\d,]+(\\.\\d+)?$");

    private final PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDType1Font serif = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);

    /** Datos del emisor. Se configuran en application.properties / variables de entorno de Render. */
    @Value("${app.empresa.razon-social:GENLOGS S.A.C.}")
    private String empresaRazonSocial;

    @Value("${app.empresa.ruc:}")
    private String empresaRuc;

    @Value("${app.empresa.direccion:}")
    private String empresaDireccion;

    @Value("${app.empresa.contacto:}")
    private String empresaContacto;

    /** Logo con nombre en src/main/resources/pdf/logo.png. Si no existe, el nombre sale en texto. */
    private byte[] logoBytes;
    private boolean logoCargado;

    /** Fotos de los repuestos/servicios: se buscan por id y se descargan de su URL (Cloudinary). */
    private final ProductoImagenRepository productoImagenRepository;
    private final ServicioImagenRepository servicioImagenRepository;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final int MAX_IMAGENES = 12;
    private static final int MAX_BYTES_IMAGEN = 8 * 1024 * 1024;

    public PdfGenerationService(ProductoImagenRepository productoImagenRepository,
                                ServicioImagenRepository servicioImagenRepository) {
        this.productoImagenRepository = productoImagenRepository;
        this.servicioImagenRepository = servicioImagenRepository;
    }

    // ════════════════════════════════════════════════════════════════════
    // DTOs
    // ════════════════════════════════════════════════════════════════════

    /**
     * Una fila de la tabla de ítems.
     * {@code detalles}: pares {etiqueta, valor} que van debajo del título en la
     * descripción, p. ej. {"MARCA", "Siemens"}, {"CÓDIGO GENLOGS", "GLB-00001"}.
     * {@code idProducto}/{@code idServicio}: para buscar la foto que va al final de la cotización.
     */
    public record LineaPdf(
            String cantidad,
            String unidad,
            String titulo,
            List<String[]> detalles,
            String precioUnitario,
            String importe,
            Long idProducto,
            Long idServicio
    ) {}

    /** DTO para datos de cotización. */
    public record CotizacionPdfData(
            String codigo,
            LocalDate fechaCotizacion,
            LocalDate fechaValidez,
            String cliente,
            String clienteDocumento,
            String clienteDireccion,
            String atencion,
            String condicionPago,
            String moneda,
            String monedaNombre,
            List<LineaPdf> lineas,
            BigDecimal subtotal,
            BigDecimal igv,
            BigDecimal total,
            /** % de IGV de la cotización (18.00). */
            BigDecimal porcentajeIgv,
            String observaciones,
            String vendedorNombre,
            String vendedorCorreo
    ) {}

    /** DTO para datos de facturación. */
    public record FacturacionPdfData(
            String tipoComprobante,
            String numeroComprobante,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento,
            String cliente,
            String clienteDocumento,
            String clienteDireccion,
            String ordenCompra,
            String cotizacion,
            String condicionPago,
            String moneda,
            String monedaNombre,
            List<LineaPdf> lineas,
            BigDecimal subtotal,
            BigDecimal igv,
            BigDecimal total,
            BigDecimal pagado,
            /** % de IGV de la factura (18.00). */
            BigDecimal porcentajeIgv,
            String observaciones
    ) {}

    /** DTO para datos de reporte genérico. `periodo` es opcional ("01/10/2026 – 31/10/2026"). */
    public record ReportePdfData(
            String titulo,
            String[] encabezados,
            String[][] datos,
            String periodo
    ) {
        /** Compatibilidad: reportes sin periodo. */
        public ReportePdfData(String titulo, String[] encabezados, String[][] datos) {
            this(titulo, encabezados, datos, null);
        }
    }

    /**
     * Arma una fila de la tabla a partir del detalle de una cotización o factura.
     * La descripción queda como en las cotizaciones de GENLOGS: título y, debajo,
     * SE OFERTA / MARCA / DESCRIPCIÓN / DESCUENTO / CÓDIGO GENLOGS.
     */
    public static LineaPdf linea(Producto producto, Servicio servicio, UnidadMedida unidad,
                                 String descripcionPersonalizada, BigDecimal cantidad,
                                 BigDecimal precioUnitario, BigDecimal descuentoUnitario,
                                 BigDecimal importe, String moneda) {
        String nombre = producto != null ? producto.getNombreProducto()
                : servicio != null ? servicio.getNombreServicio() : null;
        boolean tienePersonalizada = descripcionPersonalizada != null && !descripcionPersonalizada.isBlank();
        String titulo = tienePersonalizada ? descripcionPersonalizada.trim() : (nombre != null ? nombre : "-");

        List<String[]> detalles = new ArrayList<>();
        // Si se ofertó con otro texto, se indica qué repuesto/servicio es en realidad.
        if (tienePersonalizada && nombre != null && !nombre.equalsIgnoreCase(titulo)) {
            detalles.add(new String[] { "SE OFERTA", nombre });
        }
        if (producto != null && producto.getMarca() != null) {
            detalles.add(new String[] { "MARCA", producto.getMarca().getNombreMarca() });
        }
        String descripcion = producto != null ? producto.getDescripcion()
                : servicio != null ? servicio.getDescripcion() : null;
        if (descripcion != null && !descripcion.isBlank() && !descripcion.trim().equalsIgnoreCase(titulo)) {
            detalles.add(new String[] { "DESCRIPCIÓN", descripcion });
        }
        if (descuentoUnitario != null && descuentoUnitario.signum() > 0) {
            detalles.add(new String[] { "DESCUENTO", moneda + " " + PdfUtils.formatearMonto(descuentoUnitario) + " por unidad" });
        }
        String codigo = producto != null ? producto.getCodigoProducto()
                : servicio != null ? servicio.getCodigoServicio() : null;
        detalles.add(new String[] { "CÓDIGO GENLOGS", codigo });

        UnidadMedida um = unidad != null ? unidad
                : producto != null ? producto.getUnidadMedida()
                : servicio != null ? servicio.getUnidadMedida() : null;
        String codigoUnidad = um != null && um.getCodigoUnidad() != null ? um.getCodigoUnidad() : "UND";

        BigDecimal imp = importe;
        if (imp == null) {
            BigDecimal cant = cantidad != null ? cantidad : BigDecimal.ZERO;
            BigDecimal precio = precioUnitario != null ? precioUnitario : BigDecimal.ZERO;
            BigDecimal desc = descuentoUnitario != null ? descuentoUnitario : BigDecimal.ZERO;
            imp = cant.multiply(precio.subtract(desc)).setScale(2, RoundingMode.HALF_UP);
        }

        return new LineaPdf(
                PdfUtils.cantidadItem(cantidad),
                codigoUnidad,
                titulo,
                detalles,
                PdfUtils.formatearMonto(precioUnitario),
                PdfUtils.formatearMonto(imp),
                producto != null ? producto.getIdProducto() : null,
                servicio != null ? servicio.getIdServicio() : null);
    }

    // ════════════════════════════════════════════════════════════════════
    // Cotización
    // ════════════════════════════════════════════════════════════════════

    public byte[] generarPdfCotizacion(CotizacionPdfData d) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Lienzo lz = new Lienzo(doc);

            lz.tituloDocumento("COTIZACIÓN: " + valor(d.codigo()));
            lz.fechaDerecha("FECHA: " + PdfUtils.fechaLarga(d.fechaCotizacion()));
            lz.destinatario(d.cliente(), d.clienteDocumento(), d.clienteDireccion());

            List<String[]> datos = new ArrayList<>();
            agregarSiHay(datos, "Atención", d.atencion());
            datos.add(new String[] { "Vencimiento", PdfUtils.fechaLarga(d.fechaValidez()) });
            lz.etiquetas(datos, 80f, 10.5f, true);

            lz.espacio(10);
            lz.parrafo("Por la presente nos dirigimos a ustedes para hacerles llegar nuestra cotización por el siguiente material:", 10.5f);
            lz.espacio(6);

            lz.tablaItems(d.lineas(), PdfUtils.formatearMonto(d.subtotal()));

            lz.espacio(16);
            lz.asegurar(130); // el total y las condiciones van juntos en la misma página
            lz.lineaTexto(negrita, 11, "TOTAL, COTIZACIÓN " + d.moneda() + " " + PdfUtils.formatearMonto(d.subtotal()) + " MÁS " + TasaIgvService.comoTexto(d.porcentajeIgv()) + " I.G.V.", NEGRO);
            lz.espacio(12);

            List<String[]> condiciones = new ArrayList<>();
            agregarSiHay(condiciones, "Forma de pago", d.condicionPago());
            condiciones.add(new String[] { "Moneda", valor(d.monedaNombre()) });
            condiciones.add(new String[] { "I.G.V. (" + TasaIgvService.comoTexto(d.porcentajeIgv()) + ")", d.moneda() + " " + PdfUtils.formatearMonto(d.igv()) });
            condiciones.add(new String[] { "Total con I.G.V.", d.moneda() + " " + PdfUtils.formatearMonto(d.total()) });
            condiciones.add(new String[] { "Validez de la oferta", PdfUtils.fechaLarga(d.fechaValidez()) });
            lz.etiquetas(condiciones, 125f, 10.5f, false);

            lz.observaciones(d.observaciones());
            lz.firma(d.vendedorNombre(), "Área Comercial", d.vendedorCorreo());

            // Como en las cotizaciones de GENLOGS: las fotos de lo ofertado van al final.
            lz.anexoImagenes(imagenesDe(d.lineas()));

            return lz.terminar(out);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de la cotización", e);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Factura
    // ════════════════════════════════════════════════════════════════════

    public byte[] generarPdfFacturacion(FacturacionPdfData d) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Lienzo lz = new Lienzo(doc);

            String tipo = d.tipoComprobante() != null ? d.tipoComprobante().toUpperCase() : "COMPROBANTE";
            lz.tituloDocumento(tipo + ": " + valor(d.numeroComprobante()));
            lz.fechaDerecha("FECHA: " + PdfUtils.fechaLarga(d.fechaEmision()));
            lz.destinatario(d.cliente(), d.clienteDocumento(), d.clienteDireccion());

            List<String[]> datos = new ArrayList<>();
            agregarSiHay(datos, "Orden de compra", d.ordenCompra());
            agregarSiHay(datos, "Cotización", d.cotizacion());
            datos.add(new String[] { "Vencimiento", PdfUtils.fechaLarga(d.fechaVencimiento()) });
            lz.etiquetas(datos, 105f, 10.5f, true);

            lz.espacio(14);
            lz.tablaItems(d.lineas(), PdfUtils.formatearMonto(d.subtotal()));

            BigDecimal total = d.total() != null ? d.total() : BigDecimal.ZERO;
            BigDecimal pagado = d.pagado() != null ? d.pagado() : BigDecimal.ZERO;

            lz.espacio(16);
            lz.asegurar(140); // el total y el resumen van juntos en la misma página
            lz.lineaTexto(negrita, 11, "TOTAL A PAGAR " + d.moneda() + " " + PdfUtils.formatearMonto(total) + " (INCLUYE " + TasaIgvService.comoTexto(d.porcentajeIgv()) + " I.G.V.)", NEGRO);
            lz.espacio(12);

            List<String[]> condiciones = new ArrayList<>();
            condiciones.add(new String[] { "Subtotal", d.moneda() + " " + PdfUtils.formatearMonto(d.subtotal()) });
            condiciones.add(new String[] { "I.G.V. (" + TasaIgvService.comoTexto(d.porcentajeIgv()) + ")", d.moneda() + " " + PdfUtils.formatearMonto(d.igv()) });
            condiciones.add(new String[] { "Total", d.moneda() + " " + PdfUtils.formatearMonto(total) });
            condiciones.add(new String[] { "Pagado", d.moneda() + " " + PdfUtils.formatearMonto(pagado) });
            condiciones.add(new String[] { "Saldo pendiente", d.moneda() + " " + PdfUtils.formatearMonto(total.subtract(pagado)) });
            agregarSiHay(condiciones, "Forma de pago", d.condicionPago());
            condiciones.add(new String[] { "Moneda", valor(d.monedaNombre()) });
            lz.etiquetas(condiciones, 125f, 10.5f, false);

            lz.observaciones(d.observaciones());
            lz.firma(null, null, null);

            return lz.terminar(out);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de la facturación", e);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Reportes
    // ════════════════════════════════════════════════════════════════════

    public byte[] generarPdfReporte(ReportePdfData data) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Lienzo lz = new Lienzo(doc);
            String[] encabezados = data.encabezados();
            String[][] datos = data.datos() != null ? data.datos() : new String[0][];

            lz.tituloDocumento("REPORTE DE " + tituloReporte(data.titulo()).toUpperCase());
            lz.fechaDerecha("FECHA: " + PdfUtils.fechaLarga(LocalDate.now(ZoneId.of("America/Lima"))));

            List<String[]> info = new ArrayList<>();
            agregarSiHay(info, "Periodo", data.periodo());
            info.add(new String[] { "Registros", String.valueOf(datos.length) });
            lz.etiquetas(info, 70f, 10.5f, true);
            lz.espacio(12);

            lz.tablaReporte(encabezados, datos);
            return lz.terminar(out);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF del reporte", e);
        }
    }

    /** "COTIZACIONES" -> "Cotizaciones", "ORDENES_COMPRA" -> "Órdenes de compra". */
    private static String tituloReporte(String tipo) {
        if (tipo == null) return "";
        return switch (tipo) {
            case "COTIZACIONES" -> "Cotizaciones";
            case "ORDENES_COMPRA" -> "Órdenes de compra";
            case "FACTURACION" -> "Facturación";
            case "SERVICIOS" -> "Servicios";
            case "PRODUCTOS" -> "Repuestos";
            default -> tipo.replace('_', ' ');
        };
    }

    // ════════════════════════════════════════════════════════════════════
    // Lienzo: maneja páginas, logo, pie, saltos de página y tablas
    // ════════════════════════════════════════════════════════════════════

    /** Una línea de texto dentro de una celda (normal o en negrita). */
    private record LineaCelda(String texto, boolean negrita) {}

    private final class Lienzo {
        private final PDDocument doc;
        private PDPageContentStream cs;
        private float y;

        Lienzo(PDDocument doc) throws IOException {
            this.doc = doc;
            nuevaPagina();
        }

        /** Página nueva con el logo arriba (el pie se dibuja al final, en {@link #terminar}). */
        void nuevaPagina() throws IOException {
            if (cs != null) cs.close();
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            cs = new PDPageContentStream(doc, pagina);
            dibujarLogo();
            y = Y_CONTENIDO;
        }

        private void dibujarLogo() throws IOException {
            float yTop = ALTO_PAGINA - 36f;
            byte[] logo = obtenerLogo();
            if (logo != null) {
                try {
                    PDImageXObject imagen = PDImageXObject.createFromByteArray(doc, logo, "logo");
                    float alto = LOGO_ANCHO * imagen.getHeight() / imagen.getWidth();
                    cs.drawImage(imagen, MARGEN, yTop - alto, LOGO_ANCHO, alto);
                    return;
                } catch (IOException | IllegalArgumentException e) {
                    log.warn("No se pudo dibujar el logo en el PDF: {}", e.getMessage());
                }
            }
            texto(cs, negrita, 14, MARGEN, yTop - 20, empresaRazonSocial, NEGRO);
        }

        /** Si no entran {@code alto} puntos más en esta página, pasa a la siguiente. */
        void asegurar(float alto) throws IOException {
            if (y - alto < Y_MINIMO) nuevaPagina();
        }

        void espacio(float puntos) {
            y -= puntos;
        }

        /** "COTIZACIÓN: 20261014-01ADM" centrado, en negrita y subrayado. */
        void tituloDocumento(String titulo) throws IOException {
            float tam = 13f;
            String limpio = PdfUtils.sanitizarTextoPdf(titulo);
            float ancho = negrita.getStringWidth(limpio) / 1000f * tam;
            float x = (ANCHO_PAGINA - ancho) / 2;
            texto(cs, negrita, tam, x, y, limpio, NEGRO);
            cs.setStrokingColor(NEGRO);
            cs.setLineWidth(0.8f);
            cs.moveTo(x, y - 2.5f);
            cs.lineTo(x + ancho, y - 2.5f);
            cs.stroke();
            y -= 26;
        }

        /** "FECHA: 21 de octubre de 2026" alineado a la derecha. */
        void fechaDerecha(String texto) throws IOException {
            textoDerecha(cs, negrita, 10.5f, BORDE_DER, y, texto, NEGRO);
            y -= 22;
        }

        /** Señores: / razón social / RUC: … / dirección. */
        void destinatario(String cliente, String documento, String direccion) throws IOException {
            lineaTexto(normal, 10.5f, "Señores:", NEGRO);
            lineaTexto(negrita, 10.5f, valor(cliente), NEGRO);
            if (documento != null && !documento.isBlank()) {
                // "RUC 20114915026" -> "RUC: 20114915026"
                lineaTexto(normal, 10.5f, documento.trim().replaceFirst("^(\\S+)\\s+", "$1: "), NEGRO);
            }
            if (direccion != null && !direccion.isBlank()) {
                for (String l : envolver(direccion, normal, 10.5f, ANCHO_UTIL)) {
                    lineaTexto(normal, 10.5f, l, NEGRO);
                }
            }
            y -= 10;
        }

        /** Bloque "Etiqueta : valor" con los dos puntos alineados. */
        void etiquetas(List<String[]> filas, float anchoEtiqueta, float tam, boolean etiquetaNegrita) throws IOException {
            float interlinea = tam + 4f;
            for (String[] f : filas) {
                List<String> lineasValor = envolver(f[1], normal, tam, ANCHO_UTIL - anchoEtiqueta - 12);
                asegurar(interlinea * Math.max(1, lineasValor.size()));
                texto(cs, etiquetaNegrita ? negrita : normal, tam, MARGEN, y, f[0], NEGRO);
                texto(cs, normal, tam, MARGEN + anchoEtiqueta, y, ":", NEGRO);
                boolean primera = true;
                for (String l : lineasValor) {
                    if (!primera) y -= interlinea;
                    texto(cs, normal, tam, MARGEN + anchoEtiqueta + 10, y, l, NEGRO);
                    primera = false;
                }
                y -= interlinea;
            }
        }

        void lineaTexto(PDType1Font fuente, float tam, String texto, Color color) throws IOException {
            asegurar(tam + 4);
            texto(cs, fuente, tam, MARGEN, y, texto, color);
            y -= tam + 4f;
        }

        void parrafo(String texto, float tam) throws IOException {
            for (String l : envolver(texto, normal, tam, ANCHO_UTIL)) {
                lineaTexto(normal, tam, l, NEGRO);
            }
        }

        void observaciones(String texto) throws IOException {
            if (texto == null || texto.isBlank()) return;
            y -= 10;
            lineaTexto(negrita, 10.5f, "Observaciones:", NEGRO);
            for (String parrafo : texto.split("\\R")) {
                if (parrafo.isBlank()) continue;
                for (String l : envolver(parrafo, normal, 9.5f, ANCHO_UTIL)) {
                    lineaTexto(normal, 9.5f, l, NEGRO);
                }
            }
        }

        /** "Atentamente," + datos de quien firma (si se conocen). */
        void firma(String nombre, String cargo, String correo) throws IOException {
            asegurar(95);
            y -= 20;
            lineaTexto(normal, 10.5f, "Atentamente,", NEGRO);
            y -= 26;
            if (nombre != null && !nombre.isBlank()) {
                lineaTexto(negrita, 10.5f, nombre, NEGRO);
                if (cargo != null) lineaTexto(normal, 9.5f, cargo, GRIS_TEXTO);
                if (correo != null && !correo.isBlank()) lineaTexto(normal, 9.5f, correo, GRIS_TEXTO);
            }
            lineaTexto(negrita, 10.5f, empresaRazonSocial, NEGRO);
            if (empresaRuc != null && !empresaRuc.isBlank()) {
                lineaTexto(normal, 9.5f, "RUC " + empresaRuc.trim(), GRIS_TEXTO);
            }
        }

        /** Fotos al final: una página nueva, cada foto centrada con su ítem encima. */
        void anexoImagenes(List<ImagenItem> imagenes) throws IOException {
            if (imagenes.isEmpty()) return;
            nuevaPagina();
            float anchoMax = ANCHO_UTIL * 0.8f;
            float altoMax = (Y_CONTENIDO - Y_MINIMO) / 2f - 34f; // entran dos por página
            for (ImagenItem img : imagenes) {
                PDImageXObject imagen;
                try {
                    imagen = PDImageXObject.createFromByteArray(doc, img.bytes(), "item-" + img.item());
                } catch (IOException | IllegalArgumentException e) {
                    log.warn("Imagen del ítem {} no se pudo leer: {}", img.item(), e.getMessage());
                    continue;
                }
                float escala = Math.min(anchoMax / imagen.getWidth(), altoMax / imagen.getHeight());
                escala = Math.min(escala, 1.5f); // no agrandar demasiado las fotos chicas
                float w = imagen.getWidth() * escala;
                float h = imagen.getHeight() * escala;
                asegurar(h + 34f);
                String rotulo = "ITEM " + img.item() + " - " + img.titulo();
                textoCentrado(cs, negrita, 9.5f, ANCHO_PAGINA / 2, y,
                        PdfUtils.ajustarTexto(rotulo, negrita, 9.5f, ANCHO_UTIL));
                y -= 10f;
                cs.drawImage(imagen, (ANCHO_PAGINA - w) / 2, y - h, w, h);
                y -= h + 24f;
            }
        }

        // ── Tabla de ítems con bordes ────────────────────────────────────

        void tablaItems(List<LineaPdf> lineas, String subtotal) throws IOException {
            filaEncabezado(ENCABEZADOS_ITEMS, ANCHOS_ITEMS, true);
            int item = 10;
            for (LineaPdf l : lineas) {
                String[] fijos = { String.valueOf(item), valor(l.cantidad()), valor(l.unidad()), null,
                        valor(l.precioUnitario()), valor(l.importe()) };
                filaItem(fijos, descripcion(l));
                item += 10;
            }
            // Fila final con el subtotal en la columna TOTAL (como en la plantilla)
            float alto = INTERLINEA_TABLA + 2 * RELLENO;
            asegurar(alto);
            float yTop = y;
            bordesFila(ANCHOS_ITEMS, yTop, alto);
            float xTotal = MARGEN;
            for (int c = 0; c < 5; c++) xTotal += ANCHOS_ITEMS[c];
            textoDerecha(cs, negrita, 9, xTotal + ANCHOS_ITEMS[5] - RELLENO, yTop - RELLENO - TAM_TABLA, subtotal, NEGRO);
            y -= alto;
        }

        private List<LineaCelda> descripcion(LineaPdf l) throws IOException {
            float ancho = ANCHOS_ITEMS[3] - 2 * RELLENO;
            List<LineaCelda> out = new ArrayList<>();
            for (String t : envolver(valor(l.titulo()), negrita, TAM_TABLA, ancho)) {
                out.add(new LineaCelda(t, true));
            }
            if (l.detalles() != null) {
                for (String[] det : l.detalles()) {
                    if (det == null || det.length < 2 || det[1] == null || det[1].isBlank()) continue;
                    out.add(new LineaCelda("", false));
                    out.add(new LineaCelda(det[0] + ":", true));
                    for (String t : envolver(det[1], normal, TAM_TABLA, ancho)) {
                        out.add(new LineaCelda(t, false));
                    }
                }
            }
            return out;
        }

        /** Una fila de ítem; si la descripción no entra, continúa en la página siguiente. */
        private void filaItem(String[] fijos, List<LineaCelda> desc) throws IOException {
            int i = 0;
            boolean primera = true;
            while (i < desc.size()) {
                int caben = (int) Math.floor((y - Y_MINIMO - 2 * RELLENO) / INTERLINEA_TABLA);
                // Que el primer tramo tenga al menos 3 líneas (o la fila entera si es más corta)
                if (caben < Math.min(3, desc.size() - i)) {
                    nuevaPagina();
                    filaEncabezado(ENCABEZADOS_ITEMS, ANCHOS_ITEMS, true);
                    continue;
                }
                int hasta = Math.min(desc.size(), i + caben);
                List<LineaCelda> tramo = desc.subList(i, hasta);
                float alto = tramo.size() * INTERLINEA_TABLA + 2 * RELLENO;
                float yTop = y;
                bordesFila(ANCHOS_ITEMS, yTop, alto);

                // Columnas fijas (ITEM, CANT, U/M, precios): solo en el primer tramo,
                // centradas en la altura de la fila como en la plantilla.
                if (primera) {
                    float yTexto = yTop - alto / 2 - TAM_TABLA / 3;
                    float x = MARGEN;
                    for (int c = 0; c < fijos.length; c++) {
                        if (c >= 4) {
                            textoDerecha(cs, normal, TAM_TABLA, x + ANCHOS_ITEMS[c] - RELLENO, yTexto, fijos[c], NEGRO);
                        } else if (c != 3) {
                            textoCentrado(cs, normal, TAM_TABLA, x + ANCHOS_ITEMS[c] / 2, yTexto, fijos[c]);
                        }
                        x += ANCHOS_ITEMS[c];
                    }
                }
                float xDesc = MARGEN + ANCHOS_ITEMS[0] + ANCHOS_ITEMS[1] + ANCHOS_ITEMS[2] + RELLENO;
                float yLinea = yTop - RELLENO - TAM_TABLA;
                for (LineaCelda lc : tramo) {
                    texto(cs, lc.negrita() ? negrita : normal, TAM_TABLA, xDesc, yLinea, lc.texto(), NEGRO);
                    yLinea -= INTERLINEA_TABLA;
                }
                y -= alto;
                i = hasta;
                primera = false;
                if (i < desc.size()) {
                    nuevaPagina();
                    filaEncabezado(ENCABEZADOS_ITEMS, ANCHOS_ITEMS, true);
                }
            }
        }

        private void filaEncabezado(String[] titulos, float[] anchos, boolean centrado) throws IOException {
            float alto = TAM_ENCABEZADO + 2 * RELLENO + 2;
            asegurar(alto + INTERLINEA_TABLA * 3);
            float yTop = y;
            cs.setNonStrokingColor(GRIS_FONDO);
            cs.addRect(MARGEN, yTop - alto, suma(anchos), alto);
            cs.fill();
            cs.setNonStrokingColor(NEGRO);
            bordesFila(anchos, yTop, alto);
            float yTexto = yTop - RELLENO - TAM_ENCABEZADO + 0.5f;
            float x = MARGEN;
            for (int c = 0; c < titulos.length; c++) {
                String t = PdfUtils.ajustarTexto(titulos[c], negrita, TAM_ENCABEZADO, anchos[c] - 4);
                if (centrado) {
                    textoCentrado(cs, negrita, TAM_ENCABEZADO, x + anchos[c] / 2, yTexto, t);
                } else {
                    texto(cs, negrita, TAM_ENCABEZADO, x + RELLENO, yTexto, t, NEGRO);
                }
                x += anchos[c];
            }
            y -= alto;
        }

        private void bordesFila(float[] anchos, float yTop, float alto) throws IOException {
            cs.setStrokingColor(NEGRO);
            cs.setLineWidth(0.6f);
            cs.addRect(MARGEN, yTop - alto, suma(anchos), alto);
            float x = MARGEN;
            for (int c = 0; c < anchos.length - 1; c++) {
                x += anchos[c];
                cs.moveTo(x, yTop);
                cs.lineTo(x, yTop - alto);
            }
            cs.stroke();
        }

        // ── Tabla de reporte ─────────────────────────────────────────────

        void tablaReporte(String[] encabezados, String[][] datos) throws IOException {
            float[] anchos = anchosColumnas(encabezados, datos);
            boolean[] derecha = columnasNumericas(encabezados.length, datos);
            filaEncabezado(encabezados, anchos, false);
            float alto = INTERLINEA_TABLA + 2 * RELLENO;
            for (String[] fila : datos) {
                if (y - alto < Y_MINIMO) {
                    nuevaPagina();
                    filaEncabezado(encabezados, anchos, false);
                }
                float yTop = y;
                bordesFila(anchos, yTop, alto);
                float x = MARGEN;
                for (int c = 0; c < anchos.length; c++) {
                    String v = PdfUtils.ajustarTexto(c < fila.length ? fila[c] : "", normal, TAM_TABLA, anchos[c] - 2 * RELLENO);
                    float yTexto = yTop - RELLENO - TAM_TABLA + 0.5f;
                    if (derecha[c]) {
                        textoDerecha(cs, normal, TAM_TABLA, x + anchos[c] - RELLENO, yTexto, v, NEGRO);
                    } else {
                        texto(cs, normal, TAM_TABLA, x + RELLENO, yTexto, v, NEGRO);
                    }
                    x += anchos[c];
                }
                y -= alto;
            }
            if (datos.length == 0) {
                y -= 8;
                lineaTexto(normal, 9.5f, "No hay datos para el periodo seleccionado.", GRIS_TEXTO);
            }
        }

        private float[] anchosColumnas(String[] encabezados, String[][] datos) throws IOException {
            int n = encabezados.length;
            float[] deseado = new float[n];
            for (int c = 0; c < n; c++) {
                float max = negrita.getStringWidth(PdfUtils.sanitizarTextoPdf(encabezados[c])) / 1000f * TAM_ENCABEZADO;
                for (String[] fila : datos) {
                    if (c < fila.length) {
                        max = Math.max(max, normal.getStringWidth(PdfUtils.sanitizarTextoPdf(fila[c])) / 1000f * TAM_TABLA);
                    }
                }
                deseado[c] = Math.min(Math.max(max + 2 * RELLENO + 2, 40), 210);
            }
            float total = suma(deseado);
            float[] anchos = new float[n];
            for (int c = 0; c < n; c++) anchos[c] = deseado[c] * ANCHO_UTIL / total;
            return anchos;
        }

        /** Cierra la última página, dibuja el pie en todas y guarda. */
        byte[] terminar(ByteArrayOutputStream out) throws IOException {
            cs.close();
            int total = doc.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                try (PDPageContentStream pie = new PDPageContentStream(doc, doc.getPage(i),
                        PDPageContentStream.AppendMode.APPEND, true)) {
                    float yPie = 38f;
                    if (empresaDireccion != null && !empresaDireccion.isBlank()) {
                        textoCentrado(pie, serif, 10.5f, ANCHO_PAGINA / 2, yPie + 13, empresaDireccion);
                    }
                    if (empresaContacto != null && !empresaContacto.isBlank()) {
                        textoCentrado(pie, serif, 10.5f, ANCHO_PAGINA / 2, yPie, empresaContacto);
                    }
                    if (total > 1) {
                        textoDerecha(pie, normal, 7.5f, BORDE_DER, 20f, "Página " + (i + 1) + " de " + total, GRIS_TEXTO);
                    }
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Fotos de los ítems
    // ════════════════════════════════════════════════════════════════════

    private record ImagenItem(int item, String titulo, byte[] bytes) {}

    /** Foto principal de cada repuesto/servicio (o la primera que tenga), descargada de su URL. */
    private List<ImagenItem> imagenesDe(List<LineaPdf> lineas) {
        List<ImagenItem> out = new ArrayList<>();
        int item = 10;
        for (LineaPdf l : lineas) {
            if (out.size() >= MAX_IMAGENES) break;
            String url = urlImagen(l);
            byte[] bytes = url != null ? descargar(url) : null;
            if (bytes != null) out.add(new ImagenItem(item, l.titulo(), bytes));
            item += 10;
        }
        return out;
    }

    private String urlImagen(LineaPdf l) {
        try {
            if (l.idProducto() != null) {
                return productoImagenRepository
                        .findByProducto_IdProductoAndEsPrincipalTrueAndStatus(l.idProducto(), "A")
                        .map(i -> i.getUrlImagen())
                        .or(() -> productoImagenRepository.findByProducto_IdProductoAndStatus(l.idProducto(), "A")
                                .stream().map(i -> i.getUrlImagen()).filter(Objects::nonNull).findFirst())
                        .orElse(null);
            }
            if (l.idServicio() != null) {
                return servicioImagenRepository.findByServicio_IdServicio(l.idServicio()).stream()
                        .filter(i -> !"I".equals(i.getStatus()))
                        .sorted(Comparator.comparing(i -> !Boolean.TRUE.equals(i.getEsPrincipal())))
                        .map(i -> i.getUrlImagen())
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo buscar la imagen del ítem: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Descarga la imagen. PDFBox lee JPG/PNG/GIF/BMP pero no WebP; a las de
     * Cloudinary se les pide en JPG y a máximo 1000 px de ancho para que el PDF no pese.
     * Si falla (sin internet, URL rota, formato raro), el PDF sale igual sin esa foto.
     */
    private byte[] descargar(String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return null;
        String destino = url;
        if (url.contains("res.cloudinary.com") && url.contains("/upload/") && !url.contains("/upload/f_jpg")) {
            destino = url.replaceFirst("/upload/", "/upload/f_jpg,q_80,w_1000,c_limit/");
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(destino.replace(" ", "%20")))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() / 100 != 2 || res.body() == null || res.body().length == 0
                    || res.body().length > MAX_BYTES_IMAGEN) {
                log.warn("Imagen no disponible ({}): {}", res.statusCode(), destino);
                return null;
            }
            return res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | IllegalArgumentException e) {
            log.warn("No se pudo descargar la imagen {}: {}", destino, e.getMessage());
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Ayudas
    // ════════════════════════════════════════════════════════════════════

    private static void texto(PDPageContentStream cs, PDType1Font fuente, float tam, float x, float y,
                              String valor, Color color) throws IOException {
        String limpio = PdfUtils.sanitizarTextoPdf(valor);
        if (limpio.isEmpty()) return;
        cs.setNonStrokingColor(color);
        cs.beginText();
        cs.setFont(fuente, tam);
        cs.newLineAtOffset(x, y);
        cs.showText(limpio);
        cs.endText();
        cs.setNonStrokingColor(NEGRO);
    }

    private static void textoDerecha(PDPageContentStream cs, PDType1Font fuente, float tam, float xDerecha, float y,
                                     String valor, Color color) throws IOException {
        String limpio = PdfUtils.sanitizarTextoPdf(valor);
        float ancho = fuente.getStringWidth(limpio) / 1000f * tam;
        texto(cs, fuente, tam, xDerecha - ancho, y, limpio, color);
    }

    private static void textoCentrado(PDPageContentStream cs, PDType1Font fuente, float tam, float xCentro, float y,
                                      String valor) throws IOException {
        String limpio = PdfUtils.sanitizarTextoPdf(valor);
        float ancho = fuente.getStringWidth(limpio) / 1000f * tam;
        texto(cs, fuente, tam, xCentro - ancho / 2, y, limpio, NEGRO);
    }

    /** Parte un texto en líneas que entren en {@code ancho}; corta palabras demasiado largas. */
    private static List<String> envolver(String texto, PDType1Font fuente, float tam, float ancho) throws IOException {
        List<String> lineas = new ArrayList<>();
        String limpio = PdfUtils.sanitizarTextoPdf(texto);
        if (limpio.isEmpty()) {
            lineas.add("");
            return lineas;
        }
        StringBuilder actual = new StringBuilder();
        for (String palabra : limpio.split(" +")) {
            String candidata = actual.length() == 0 ? palabra : actual + " " + palabra;
            if (fuente.getStringWidth(candidata) / 1000f * tam <= ancho) {
                actual.setLength(0);
                actual.append(candidata);
                continue;
            }
            if (actual.length() > 0) {
                lineas.add(actual.toString());
                actual.setLength(0);
            }
            // Palabra más larga que el ancho: se corta por caracteres
            String resto = palabra;
            while (fuente.getStringWidth(resto) / 1000f * tam > ancho && resto.length() > 1) {
                int corte = resto.length() - 1;
                while (corte > 1 && fuente.getStringWidth(resto.substring(0, corte)) / 1000f * tam > ancho) corte--;
                lineas.add(resto.substring(0, corte));
                resto = resto.substring(corte);
            }
            actual.append(resto);
        }
        if (actual.length() > 0) lineas.add(actual.toString());
        return lineas;
    }

    /** Columnas cuyos valores son todos números ("4,698.76"): se alinean a la derecha. */
    private static boolean[] columnasNumericas(int n, String[][] datos) {
        boolean[] numericas = new boolean[n];
        for (int c = 0; c < n; c++) {
            boolean alguna = false;
            boolean todas = true;
            for (String[] fila : datos) {
                String v = c < fila.length && fila[c] != null ? fila[c].trim() : "";
                if (v.isEmpty()) continue;
                alguna = true;
                if (!NUMERICO.matcher(v).matches()) {
                    todas = false;
                    break;
                }
            }
            numericas[c] = alguna && todas;
        }
        return numericas;
    }

    private static float suma(float[] valores) {
        float s = 0;
        for (float v : valores) s += v;
        return s;
    }

    private static void agregarSiHay(List<String[]> lista, String etiqueta, String valor) {
        if (valor != null && !valor.isBlank()) {
            lista.add(new String[] { etiqueta, valor });
        }
    }

    private static String valor(String v) {
        return v != null ? v : "";
    }

    private synchronized byte[] obtenerLogo() {
        if (!logoCargado) {
            logoCargado = true;
            ClassPathResource recurso = new ClassPathResource("pdf/logo.png");
            if (recurso.exists()) {
                try (InputStream in = recurso.getInputStream()) {
                    logoBytes = in.readAllBytes();
                } catch (IOException e) {
                    log.warn("No se pudo leer pdf/logo.png: {}", e.getMessage());
                }
            }
        }
        return logoBytes;
    }
}