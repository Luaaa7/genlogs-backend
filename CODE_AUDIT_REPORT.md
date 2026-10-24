# Informe de Auditoría de Código - GenLogs Backend

**Fecha:** 20/10/2026  
**Auditor:** Principal Software Architect / Lead Code Auditor  
**Alcance:** src/main/java/com/genlogs/app (Spring Boot 3.x, Java 21, PostgreSQL/Supabase)  
**Prioridad Global de Refactorización:** ALTA

---

## 1. Resumen Ejecutivo

El código base presenta **deuda técnica significativa** concentrada en tres servicios monolíticos ("God Services") que violan el Principio de Responsabilidad Única (SRP), duplican lógica de generación de PDF, y acoplan reglas de negocio con preocupaciones de infraestructura. Aunque la configuración de seguridad (JWT, CORS, validación de secretos) y el manejo global de excepciones son correctos, la arquitectura de servicios requiere una refactorización urgente para garantizar mantenibilidad, testabilidad y escalabilidad.

**Métricas Clave:**
| Componente | Líneas | Responsabilidades | Estado |
|------------|--------|-------------------|--------|
| CotizacionService | ~1,100 | CRUD, PDF, Email, Archivos, Estados, Seguimiento | CRÍTICO |
| FacturacionService | ~780 | CRUD, PDF, Pagos, Estados, Vistas nativas | CRÍTICO |
| ProductoService | ~820 | CRUD, Imágenes, Docs, Características, Proveedores | ALTO |
| ReporteService | ~500 | Excel/PDF genérico, 5 tipos de reporte | ALTO |

---

## 2. Hallazgos Críticos (Mala Praxis & Bugs Atómicos)

### 2.1 God Services - Violación Grave de SRP (SOLID)
**Archivos:** CotizacionService.java, FacturacionService.java, ProductoService.java

Cada servicio concentra 5-8 responsabilidades distintas:
- CotizacionService: CRUD + Generación PDF (200+ líneas) + Envío email + Gestión adjuntos + Seguimiento comercial + Duplicación + Estadísticas
- FacturacionService: CRUD + Generación PDF (180+ líneas) + Registro pagos + Cambio estados + Consultas nativas
- ProductoService: CRUD + Subida imágenes/documentos (Cloudinary) + Características técnicas + Proveedores + Sectores

**Impacto:** Imposible testear unitariamente, alto acoplamiento, cambios en PDF rompen lógica de negocio, violación de Open/Closed Principle.

---

### 2.2 Duplicación Masiva de Lógica de PDF (DRY Violado)
**Archivos:** 
- CotizacionService.java (líneas 400-696): generarPdf(), dibujarTabla(), sanitizarTextoPdf(), ajustarTexto()
- FacturacionService.java (líneas 200-450): generarPdf(), dibujarTabla(), sanitizarTextoPdf(), ajustarTexto()
- ReporteService.java (líneas 253-309): generarPdf(), escribirFila()

**Evidencia:** ~400 líneas de código idéntico/semánticamente equivalente replicadas en 3 servicios. Cualquier corrección (fuentes, márgenes, encoding) requiere 3 cambios sincronizados.

---

### 2.3 Bloque catch Vacío - Pérdida Silenciosa de Errores
**Archivo:** CotizacionService.java  
**Líneas:** 750-751
```java
} catch (Exception ignored) {
}
```
En obtenerNombreCliente(): cualquier excepción (NPE, LazyInitialization, etc.) se traga silenciosamente retornando "Cliente". **Riesgo:** Datos corruptos en PDFs/envios sin trazabilidad.

---

### 2.4 N+1 Select Problem en Mappers toResponse()
**Archivos:** Múltiples servicios (CotizacionService, FacturacionService, OrdenCompraService, ProductoService)

```java
// CotizacionService.toResponse() - línea ~380
String clienteNombre = obtenerNombreCliente(c); // Navega: c.getCliente().getTercero().getRazonSocial()

// FacturacionService.toResponse() - línea 486
String clienteNombre = obtenerNombreCliente(f); // Navega: f.getOrdenCompra().getCotizacion().getCliente().getTercero()

// OrdenCompraService.toResponse() - línea 198
oc.getCotizacion().getCliente().getTercero().getRazonSocial()
```

**Impacto:** En listados paginados de 20-100 items, se ejecutan 20-100 queries adicionales por lista. No usan JOIN FETCH ni @EntityGraph.

---

### 2.5 Falta de Paginación en Endpoints de Listado Completo
**Archivos/Endpoints:**
- CotizacionController.listarPorCliente() → CotizacionService.listarPorCliente() retorna List<CotizacionResponse> sin paginación
- FacturacionController.listarPorCliente() → FacturacionService.listarPorCliente() retorna List<FacturacionResponse>
- OrdenCompraService.listarPorCliente() retorna List<OrdenCompraResponse>

**Riesgo:** Cliente con 10,000+ cotizaciones/facturas → OutOfMemoryError o timeout HTTP.

---

### 2.6 Magic Strings para Estados ("A", "I", "ACTIVO", "INACTIVO")
**Archivos:** Repositories, Services, Entities (ej. ProductoRepository.findByStatusAndVisibleWeb("A", true), ClienteService.desactivar() setea "I")

**Impacto:** Typos no detectados en compile-time, refactoring frágil, acoplamiento a esquema de BD.

---

### 2.7 Transacciones en Métodos de Solo Lectura Incorrectos
**Archivo:** OrdenCompraService.agregarArchivo() (línea 140-156)

Problema: Si falla paso 3, queda archivo huérfano en Cloudinary. Si falla paso 2 tras paso 1, queda URL en BD sin archivo. **No hay atomicidad ni compensación (Saga).**

---

### 2.8 Inyección de Dependencias Excesiva (God Service Constructor)
**Archivo:** ProductoService.java (líneas 62-71) - **15 dependencias inyectadas**

**Violación:** Interface Segregation Principle - el servicio conoce repositorios que solo usan sub-dominios (imágenes, docs, proveedores).

---

### 2.9 Validación de Negocio Duplicada en BD y Java
**Archivo:** ClienteService.validarLongitudDocumento() (líneas 133-142) comenta: "Misma regla que valida el trigger fn_tercero_valida_documento() en la BD"

**Riesgo:** Divergencia silenciosa entre validación Java y trigger PL/pgSQL. Una sola fuente de verdad (DB o Domain) es preferible.

---

### 2.10 Hardcoded Cloudinary Fallback URLs en Producción
**Archivo:** CloudinaryService.subir() (líneas 117-120)

**Riesgo:** URLs externas no controladas en respuestas de API de producción si falla config. Debe retornar error 503 o placeholder local.
---

## 3. Oportunidades de Mejora (Buenas Prácticas)

### 3.1 Extraer PdfGenerationService Común
Crear un servicio dedicado PdfGenerationService con:
- generateCotizacionPdf(CotizacionData) 
- generateFacturacionPdf(FacturacionData)
- generateReportePdf(ReportData)
- Utilidades compartidas: sanitizarTextoPdf, ajustarTexto, dibujarTabla

**Beneficio:** Elimina 400 líneas duplicadas, centraliza cambios de formato, testable independientemente.

---

### 3.2 Introducir DTOs de Proyección / @EntityGraph para Listados
En repositorios, definir proyecciones (interface CotizacionListProjection) con solo campos necesarios para listados, evitando carga de relaciones LAZY.

```java
@Query("SELECT new com.genlogs.app.dto.CotizacionListItem(c.id, c.codigo, cli.razonSocial, ...) " +
       "FROM Cotizacion c JOIN c.cliente cli JOIN cli.tercero t WHERE c.status = 'A'")
Page<CotizacionListItem> findAllForList(Pageable pageable);
```

---

### 3.3 Separar ProductoService en Servicios de Dominio Cohesivos
| Nuevo Servicio | Responsabilidad | Repositorios |
|----------------|-----------------|--------------|
| ProductoCoreService | CRUD básico, categoría, marca, unidad | Producto, Categoria, Marca, Unidad |
| ProductoImagenService | Imágenes (Cloudinary) | ProductoImagen |
| ProductoDocumentoService | Documentos (Cloudinary) | DocumentoProducto |
| ProductoCaracteristicaService | Características técnicas | ProductoCaracteristica, CaracteristicaTecnica |
| ProductoProveedorService | Proveedores asociados | ProductoProveedor, Proveedor |

---

### 3.4 Implementar Patrones de Resiliencia para Cloudinary
- **Circuit Breaker** (Resilience4j) alrededor de CloudinaryService
- **Retry con backoff** para subidas/eliminaciones
- **Compensación transaccional**: si falla BD tras subir a Cloudinary, programar limpieza asíncrona (outbox pattern / @EventListener)

---

### 3.5 Reemplazar Magic Strings por Enums/Constants
```java
public enum EntityStatus { ACTIVO("A"), INACTIVO("I"); }
public enum ClienteSituacion { ACTIVO, INACTIVO; }
```
Uso: cliente.setSituacion(ClienteSituacion.INACTIVO.name())

---

### 3.6 Añadir Paginación Obligatoria en Todos los Listados
Cambiar signatures:
```java
// Antes
List<CotizacionResponse> listarPorCliente(Long idCliente);

// Después
Page<CotizacionResponse> listarPorCliente(Long idCliente, Pageable pageable);
```
Y en controllers validar size <= MAX_PAGE_SIZE (100).

---

### 3.7 Centralizar Auditoría (userCreate, processCreate, etc.)
Crear AuditorAwareImpl + @EntityListeners(AuditingEntityListener.class) + @CreatedBy, @LastModifiedBy en entidad base Auditable. Eliminar seteo manual repetido en 30+ métodos.

---

### 3.8 Validación Bean Validation en DTOs vs Excepciones Manuales
Migrar validaciones BusinessException en servicios a anotaciones @NotNull, @Size, @Pattern en DTOs + @Valid en controllers. Ejemplo: ClienteRequest.numeroDocumento → @Size(min=8, max=11) @Pattern(regexp="\\d+").

---

### 3.9 Documentación OpenAPI/Swagger Faltante
Añadir springdoc-openapi-starter-webmvc-ui y anotar controllers con @Operation, @Parameter, @ApiResponse. Genera contrato vivo para frontend.

---

### 3.10 Tests de Integración Ausentes
Solo existe AppApplicationTests.java (context loads). Añadir:
- @SpringBootTest + Testcontainers (PostgreSQL) para repositorios
- @WebMvcTest para controllers
- @MockBean services para tests unitarios de lógica pura

---

## 4. Plan de Acción / Roadmap de Refactorización (Priorizado)

| # | Acción | Esfuerzo | Riesgo | Prioridad |
|---|--------|----------|--------|-----------|
| 1 | Fix: Eliminar catch (Exception ignored) en CotizacionService.obtenerNombreCliente() | 15 min | Bajo | INMEDIATO |
| 2 | Fix: Añadir paginación a listarPorCliente en Cotizacion, Facturacion, OrdenCompra | 2 h | Bajo | INMEDIATO |
| 3 | Refactor: Extraer PdfGenerationService y migrar 3 servicios | 1-2 días | Medio | SPRINT 1 |
| 4 | Refactor: Split ProductoService en 5 servicios cohesivos | 2-3 días | Medio | SPRINT 1 |
| 5 | Optimización: @EntityGraph / Proyecciones para listados (eliminar N+1) | 1 día | Bajo | SPRINT 1 |
| 6 | Refactor: Split CotizacionService (PDF, Email, Adjuntos, Seguimiento → servicios separados) | 2-3 días | Medio | SPRINT 2 |
| 7 | Refactor: Split FacturacionService (PDF, Pagos → servicios separados) | 1-2 días | Medio | SPRINT 2 |
| 8 | Infra: Circuit Breaker + Retry para Cloudinary | 4 h | Bajo | SPRINT 2 |
| 9 | Limpieza: Magic Strings → Enums (Status, Situación, Tipos) | 4 h | Bajo | SPRINT 3 |
| 10 | Auditoría: @EntityListeners + AuditorAware eliminar seteo manual | 4 h | Bajo | SPRINT 3 |
| 11 | Validación: Bean Validation en DTOs | 1 día | Bajo | SPRINT 3 |
| 12 | Docs: OpenAPI/Swagger | 4 h | Bajo | SPRINT 3 |
| 13 | Tests: Suite de integración + unitarios (cobertura > 70%) | 3-5 días | - | CONTINUO |
---

## 5. Propuestas de Código (Top 3 Hallazgos Graves)

### 5.1 Hallazgo #1: Bloque catch Vacío en CotizacionService

**ANTES (Líneas 738-753):**
```java
private String obtenerNombreCliente(Cotizacion c) {
    try {
        if (c.getCliente() != null && c.getCliente().getTercero() != null) {
            String rs = c.getCliente().getTercero().getRazonSocial();
            if (rs != null && !rs.isBlank()) return rs;
            String doc = c.getCliente().getTercero().getNumeroDocumento();
            if (doc != null && !doc.isBlank()) return doc;
        }
    } catch (Exception ignored) { }  // MALA PRAXIS: traga NPE, LazyInit, etc.
    return "Cliente";
}
```

**DESPUÉS:**
```java
private String obtenerNombreCliente(Cotizacion c) {
    if (c == null || c.getCliente() == null || c.getCliente().getTercero() == null) {
        log.debug("Datos de cliente incompletos para cotización {}", c != null ? c.getIdCotizacion() : "null");
        return "Cliente";
    }
    Tercero tercero = c.getCliente().getTercero();
    return Optional.ofNullable(tercero.getRazonSocial())
            .filter(String::isNotBlank)
            .or(() -> Optional.ofNullable(tercero.getNumeroDocumento()).filter(String::isNotBlank))
            .orElse("Cliente");
}
```
*Elimina try-catch innecesario, usa Optional chain, logea contexto para debugging.*
---

### 5.2 Hallazgo #2: Duplicación de Lógica PDF (Extracción a Servicio Común)

**ANTES:** ~400 líneas duplicadas en CotizacionService, FacturacionService, ReporteService

**DESPUÉS - Nuevo PdfGenerationService.java:**
```java
@Service
@RequiredArgsConstructor
public class PdfGenerationService {

    private static final PDType1Font FONT_TITLE = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font FONT_BODY  = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FONT_TABLE_HEADER = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font FONT_TABLE_CELL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    public record TableColumn(String header, float widthRatio, Function<Object, String> mapper) {}

    public byte[] generateTablePdf(String title, List<?> data, List<TableColumn> columns, 
                                   String filename, Consumer<PdfConfig> configurer) {
        PdfConfig config = new PdfConfig();
        configurer.accept(config);
        
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(config.pageSize);
            doc.addPage(page);
            
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = drawTitle(cs, title, config.marginLeft, page.getMediaBox().getHeight() - 50);
                y = drawTableHeader(cs, columns, config.marginLeft, y, config.columnWidths);
                
                for (Object row : data) {
                    if (y < config.bottomMargin) {
                        cs.close();
                        page = new PDPage(config.pageSize);
                        doc.addPage(page);
                        cs = new PDPageContentStream(doc, page);
                        y = page.getMediaBox().getHeight() - 50;
                        y = drawTableHeader(cs, columns, config.marginLeft, y, config.columnWidths);
                    }
                    y = drawTableRow(cs, row, columns, config.marginLeft, y, config.columnWidths);
                }
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Error generando PDF: " + filename, e);
        }
    }

    // Métodos privados: drawTitle, drawTableHeader, drawTableRow, sanitizeText, fitText...
    // Centralizados, testeables, reutilizables.
}
```

**Uso en CotizacionService:**
```java
@Autowired private PdfGenerationService pdfService;

public byte[] descargarPdf(Long id) {
    Cotizacion c = obtenerOLanzar(id);
    List<CotizacionDetalle> lineas = cotizacionDetalleRepository.findByCotizacion_IdCotizacion(id);
    
    return pdfService.generateTablePdf(
        "Cotización " + c.getCodigoCotizacion(),
        lineas,
        List.of(
            new PdfGenerationService.TableColumn("Ítem", 0.05f, r -> String.valueOf(lineas.indexOf(r)+1)),
            new PdfGenerationService.TableColumn("Descripción", 0.45f, r -> sanitize(((CotizacionDetalle)r).getDescripcionPersonalizada())),
            new PdfGenerationService.TableColumn("Cant.", 0.10f, r -> ((CotizacionDetalle)r).getCantidad().toString()),
            new PdfGenerationService.TableColumn("P. Unit.", 0.15f, r -> ((CotizacionDetalle)r).getPrecioUnitario().toString()),
            new PdfGenerationService.TableColumn("Desc.", 0.10f, r -> ((CotizacionDetalle)r).getDescuentoUnitario().toString()),
            new PdfGenerationService.TableColumn("Importe", 0.15f, r -> ((CotizacionDetalle)r).getImporteLinea().toString())
        ),
        "cotizacion-" + id + ".pdf",
        cfg -> cfg.setPageSize(PDRectangle.A4).setMarginLeft(40)
    );
}
```
*Reduce CotizacionService en ~200 líneas, elimina duplicación, permite testing aislado de PDF.*
---

### 5.3 Hallazgo #3: N+1 en Listados + Falta de Paginación

**ANTES - CotizacionRepository:**
```java
@Query("SELECT c FROM Cotizacion c WHERE c.status = 'A' AND c.cliente.idCliente = :idCliente ORDER BY c.fechaCotizacion DESC")
List<Cotizacion> findByCliente(@Param("idCliente") Long idCliente);  // Sin paginación
```

**ANTES - CotizacionService.listarPorCliente():**
```java
public List<CotizacionResponse> listarPorCliente(Long idCliente) {
    return cotizacionRepository.findByCliente(idCliente).stream()
            .map(this::toResponse)  // N+1: toResponse navega cliente.tercero.razonSocial
            .collect(Collectors.toList());
}
```

**DESPUÉS - Repository con Proyección:**
```java
public interface CotizacionListProjection {
    Long getIdCotizacion();
    String getCodigoCotizacion();
    LocalDate getFechaCotizacion();
    String getClienteRazonSocial();  // Viene del JOIN
    String getEstadoNombre();
    String getMonedaSimbolo();
    BigDecimal getTotal();  // De vista nativa
}

@Query(value = """
    SELECT c.id_cotizacion as idCotizacion, c.codigo_cotizacion as codigoCotizacion,
           c.fecha_cotizacion as fechaCotizacion, t.razon_social as clienteRazonSocial,
           ec.nombre_estado as estadoNombre, m.simbolo as monedaSimbolo,
           v.total as total
    FROM cotizacion c
    JOIN cliente cl ON cl.id_cliente = c.id_cliente
    JOIN tercero t ON t.id_tercero = cl.id_tercero
    JOIN estado_cotizacion ec ON ec.id_estado_cotizacion = c.id_estado_cotizacion
    JOIN moneda m ON m.id_moneda = c.id_moneda
    LEFT JOIN vw_cotizacion_totales v ON v.id_cotizacion = c.id_cotizacion
    WHERE c.status = 'A' AND cl.id_cliente = :idCliente
    ORDER BY c.fecha_cotizacion DESC
    """, nativeQuery = true)
Page<CotizacionListProjection> findByClientePaginado(@Param("idCliente") Long idCliente, Pageable pageable);
```

**DESPUÉS - Service:**
```java
public Page<CotizacionListResponse> listarPorCliente(Long idCliente, Pageable pageable) {
    return cotizacionRepository.findByClientePaginado(idCliente, pageable)
            .map(this::toListResponse);  // Mapeo plano, sin navegación LAZY
}

private CotizacionListResponse toListResponse(CotizacionListProjection p) {
    return CotizacionListResponse.builder()
            .idCotizacion(p.getIdCotizacion())
            .codigoCotizacion(p.getCodigoCotizacion())
            .fechaCotizacion(p.getFechaCotizacion())
            .clienteRazonSocial(p.getClienteRazonSocial())
            .estado(p.getEstadoNombre())
            .moneda(p.getMonedaSimbolo())
            .total(p.getTotal())
            .build();
}
```

**DESPUÉS - Controller:**
```java
@GetMapping("/cliente/{idCliente}")
public ResponseEntity<Page<CotizacionListResponse>> listarPorCliente(
        @PathVariable Long idCliente,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("fechaCotizacion").descending());
    return ResponseEntity.ok(cotizacionService.listarPorCliente(idCliente, pageable));
}
```
*Elimina N+1, añade paginación segura, usa proyección nativa eficiente.*

---

## 6. Conclusión

El código base de GenLogs Backend es **funcionalmente correcto** pero **arquitectónicamente frágil**. Los tres hallazgos críticos (God Services, duplicación PDF, catch vacíos) representan riesgo operativo inmediato. La ejecución del roadmap propuesto en 3 sprints transformará la base en una arquitectura modular, testeable y escalable, alineada con principios SOLID y Clean Architecture.

**Próximo paso recomendado:** Iniciar **Sprint 1** con las correcciones inmediatas (#1, #2) y la extracción de PdfGenerationService + split de ProductoService en paralelo.

--- 

*Fin del Informe de Auditoría*