package com.genlogs.app.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.genlogs.app.service.OrdenCompraService;
import com.genlogs.app.dto.AdjuntoCotizacionResponse;
import com.genlogs.app.dto.CambiarEstadoCotizacionRequest;
import com.genlogs.app.dto.CotizacionDetalleResponse;
import com.genlogs.app.dto.CotizacionRequest;
import com.genlogs.app.dto.CotizacionResponse;
import com.genlogs.app.dto.EnviarCotizacionRequest;
import com.genlogs.app.dto.EnviarCotizacionResponse;
import com.genlogs.app.dto.EstadisticasCotizacionResponse;
import com.genlogs.app.dto.PaginaResponse;
import com.genlogs.app.dto.SeguimientoCotizacionResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.service.CotizacionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cotizaciones")
@RequiredArgsConstructor
public class CotizacionController {

    private static final Logger log = LoggerFactory.getLogger(CotizacionController.class);

    private final CotizacionService cotizacionService;
    private final OrdenCompraService ordenCompraService;

    /**
     * Lista blanca de campos de ordenamiento del listado.
     * Clave = valor que acepta el frontend (sortBy).
     * Valor = atributo real de la entidad Cotizacion por el que se ordena.
     *
     * Sin esta lista blanca, Spring Data JPA inyecta el sortBy tal cual en el
     * ORDER BY del JPQL de CotizacionRepository.buscarPaginado y Hibernate
     * falla con "Could not resolve attribute ..." cuando el cliente manda un
     * campo que no existe en la entidad (p.ej. "fechaCreacion"), provocando
     * un HTTP 500 en toda la pantalla de cotizaciones.
     */
    private static final Map<String, String> CAMPOS_ORDENAMIENTO = new LinkedHashMap<>();

    static {
        CAMPOS_ORDENAMIENTO.put("fechaCotizacion", "fechaCotizacion");
        // Alias histórico del frontend: la entidad NO tiene "fechaCreacion"
        // (la fecha de alta es Auditable.dateCreate, columna date_create).
        CAMPOS_ORDENAMIENTO.put("fechaCreacion", "fechaCotizacion");
        CAMPOS_ORDENAMIENTO.put("dateCreate", "dateCreate");
        CAMPOS_ORDENAMIENTO.put("codigoCotizacion", "codigoCotizacion");
        CAMPOS_ORDENAMIENTO.put("idCotizacion", "idCotizacion");
        CAMPOS_ORDENAMIENTO.put("estado", "estadoCotizacion.nombreEstado");
        CAMPOS_ORDENAMIENTO.put("cliente", "cliente.tercero.razonSocial");
    }

    /**
     * Listado paginado con filtros, consumido por CotizacionesListPage.tsx
     * (cotizacionesApi.listarCotizaciones). Los nombres de parámetros
     * (estadoCotizacion, clienteId, moneda, page, size, sortBy, sortDir)
     * coinciden con CotizacionesFilterParams del frontend.
     */
    @GetMapping
    public ResponseEntity<PaginaResponse<CotizacionResponse>> listar(
            @RequestParam(required = false) String estadoCotizacion,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) String moneda,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "fechaCotizacion") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        Sort sort = construirSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
        return ResponseEntity.ok(cotizacionService.listar(estadoCotizacion, clienteId, moneda, pageable));
    }

    /**
     * Traduce el sortBy del frontend a un atributo válido de la entidad
     * Cotizacion y valida la dirección. Un campo o una dirección no válidos
     * devuelven 400 (BusinessException) en lugar de reventar con 500 al
     * construir la consulta.
     */
    private Sort construirSort(String sortBy, String sortDir) {
        String clave = sortBy == null ? "" : sortBy.trim();
        String atributo = CAMPOS_ORDENAMIENTO.get(clave);
        if (atributo == null) {
            throw new BusinessException("Campo de ordenamiento inválido: '" + sortBy + "'. Campos permitidos: "
                    + String.join(", ", CAMPOS_ORDENAMIENTO.keySet()));
        }

        String direccion = sortDir == null ? "" : sortDir.trim().toUpperCase(Locale.ROOT);
        Sort.Direction direction;
        if ("ASC".equals(direccion)) {
            direction = Sort.Direction.ASC;
        } else if ("DESC".equals(direccion)) {
            direction = Sort.Direction.DESC;
        } else {
            throw new BusinessException("Dirección de ordenamiento inválida: '" + sortDir + "'. Use ASC o DESC.");
        }

        return Sort.by(direction, atributo);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CotizacionResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(cotizacionService.buscarPorId(id));
    }

    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<CotizacionResponse> buscarPorCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok(cotizacionService.buscarPorCodigo(codigo));
    }

    @GetMapping("/cliente/{idCliente}")
    public ResponseEntity<PaginaResponse<CotizacionResponse>> listarPorCliente(
            @PathVariable Long idCliente,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("fechaCotizacion").descending());
        return ResponseEntity.ok(cotizacionService.listar(null, idCliente, null, pageable));
    }

    @GetMapping("/{id}/lineas")
    public ResponseEntity<List<CotizacionDetalleResponse>> listarLineas(@PathVariable Long id) {
        return ResponseEntity.ok(cotizacionService.listarLineas(id));
    }

    @GetMapping("/{id}/seguimiento")
    public ResponseEntity<List<SeguimientoCotizacionResponse>> listarSeguimiento(@PathVariable Long id) {
        return ResponseEntity.ok(cotizacionService.listarSeguimiento(id));
    }

    @GetMapping("/{id}/adjuntos")
    public ResponseEntity<List<AdjuntoCotizacionResponse>> listarAdjuntos(@PathVariable Long id) {
        return ResponseEntity.ok(cotizacionService.listarAdjuntos(id));
    }

    /**
     * Estadísticas agregadas para CotizacionesListPage.tsx
     * (cotizacionesApi.obtenerEstadisticas). Nota: se declara antes de
     * "/{id}" solo por orden de lectura — Spring ya prioriza el segmento
     * literal "estadisticas" sobre la variable {id}, así que no hay riesgo
     * de que "estadisticas" se intente parsear como Long.
     */
    @GetMapping("/estadisticas")
    public ResponseEntity<EstadisticasCotizacionResponse> obtenerEstadisticas() {
        return ResponseEntity.ok(cotizacionService.obtenerEstadisticas());
    }

    @PostMapping
    public ResponseEntity<CotizacionResponse> crear(@Valid @RequestBody CotizacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cotizacionService.crear(request));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<CotizacionResponse> cambiarEstado(
            @PathVariable Long id, @Valid @RequestBody CambiarEstadoCotizacionRequest request) {
        CotizacionResponse respuesta = cotizacionService.cambiarEstado(id, request);
        // Aprobada => se registra sola su orden de compra. Va después de guardar el estado
        // (otra transacción): si la orden fallara, la aprobación igual queda.
        if ("APROBADA".equalsIgnoreCase(respuesta.getEstadoCodigo())) {
            try {
                ordenCompraService.crearAutomatica(id);
            } catch (Exception e) {
                log.warn("No se pudo registrar sola la orden de la cotización {}: {}", id, e.getMessage());
            }
        }
        return ResponseEntity.ok(respuesta);
    }

    @PostMapping(value = "/{id}/adjuntos", consumes = "multipart/form-data")
    public ResponseEntity<AdjuntoCotizacionResponse> agregarAdjunto(
            @PathVariable Long id, @RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cotizacionService.agregarAdjunto(id, archivo));
    }

    @DeleteMapping("/{idCotizacion}/adjuntos/{idAdjunto}")
    public ResponseEntity<Void> eliminarAdjunto(@PathVariable Long idCotizacion, @PathVariable Long idAdjunto) {
        cotizacionService.eliminarAdjunto(idCotizacion, idAdjunto);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enviar")
    public ResponseEntity<EnviarCotizacionResponse> enviar(
            @PathVariable Long id, @Valid @RequestBody EnviarCotizacionRequest request) {
        return ResponseEntity.ok(cotizacionService.enviar(id, request));
    }

    // Alias /pdf para compatibilidad total con frontend
    @GetMapping(value = {"/{id}/descargar-pdf", "/{id}/pdf"})
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Long id) {
        byte[] pdf = cotizacionService.descargarPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cotizacion-" + id + ".pdf\"")
                .body(pdf);
    }

    @PostMapping("/{id}/duplicar")
    public ResponseEntity<CotizacionResponse> duplicar(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cotizacionService.duplicar(id));
    }
}