package com.genlogs.app.controller;

import com.genlogs.app.dto.ProductoCaracteristicaRequest;
import com.genlogs.app.dto.ProductoProveedorRequest;
import com.genlogs.app.dto.ProductoRequest;
import com.genlogs.app.dto.ProductoResponse;
import com.genlogs.app.model.TipoDocumentoProducto;
import com.genlogs.app.service.ProductoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    @PostMapping
    // @PreAuthorize("hasAnyRole('ADMINISTRADOR','VENDEDOR')") // lo agrega Luana con MethodSecurityConfig
    public ResponseEntity<ProductoResponse> registrar(@Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productoService.registrar(request));
    }

    @PutMapping("/{idProducto}")
    public ResponseEntity<ProductoResponse> actualizar(@PathVariable Long idProducto,
                                                         @Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.ok(productoService.actualizar(idProducto, request));
    }

    @GetMapping("/{idProducto}")
    public ResponseEntity<ProductoResponse> buscarPorId(@PathVariable Long idProducto) {
        return ResponseEntity.ok(productoService.buscarPorId(idProducto));
    }

    @GetMapping("/codigo/{codigoProducto}")
    public ResponseEntity<ProductoResponse> buscarPorCodigo(@PathVariable String codigoProducto) {
        return ResponseEntity.ok(productoService.buscarPorCodigo(codigoProducto));
    }

    /** Catálogo web público: solo productos activos y visibles. */
    @GetMapping
    public ResponseEntity<List<ProductoResponse>> listarVisiblesWeb() {
        return ResponseEntity.ok(productoService.listarVisiblesWeb());
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<ProductoResponse>> buscarPorNombre(@RequestParam String nombre) {
        return ResponseEntity.ok(productoService.buscarPorNombre(nombre));
    }

    @DeleteMapping("/{idProducto}")
    public ResponseEntity<Void> desactivar(@PathVariable Long idProducto) {
        productoService.desactivar(idProducto);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Ficha técnica
    // ------------------------------------------------------------------

    @PostMapping("/{idProducto}/caracteristicas")
    public ResponseEntity<Void> agregarCaracteristica(@PathVariable Long idProducto,
                                                        @Valid @RequestBody ProductoCaracteristicaRequest request) {
        productoService.agregarCaracteristica(idProducto, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{idProducto}/caracteristicas/{idCaracteristica}")
    public ResponseEntity<Void> quitarCaracteristica(@PathVariable Long idProducto,
                                                       @PathVariable Integer idCaracteristica) {
        productoService.quitarCaracteristica(idProducto, idCaracteristica);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Imágenes (multipart, subidas a Cloudinary)
    // ------------------------------------------------------------------

    @PostMapping(value = "/{idProducto}/imagenes", consumes = "multipart/form-data")
    public ResponseEntity<Void> subirImagen(@PathVariable Long idProducto,
                                             @RequestParam("archivo") MultipartFile archivo,
                                             @RequestParam(defaultValue = "false") boolean esPrincipal) {
        productoService.subirImagen(idProducto, archivo, esPrincipal);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/imagenes/{idProductoImagen}")
    public ResponseEntity<Void> eliminarImagen(@PathVariable Long idProductoImagen) {
        productoService.eliminarImagen(idProductoImagen);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Documentos (multipart, subidos a Cloudinary)
    // ------------------------------------------------------------------

    @PostMapping(value = "/{idProducto}/documentos", consumes = "multipart/form-data")
    public ResponseEntity<Void> subirDocumento(@PathVariable Long idProducto,
                                                @RequestParam("archivo") MultipartFile archivo,
                                                @RequestParam TipoDocumentoProducto tipo,
                                                @RequestParam String nombreDocumento) {
        productoService.subirDocumento(idProducto, archivo, tipo, nombreDocumento);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/documentos/{idDocumento}")
    public ResponseEntity<Void> eliminarDocumento(@PathVariable Long idDocumento) {
        productoService.eliminarDocumento(idDocumento);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Sectores económicos
    // ------------------------------------------------------------------

    @PostMapping("/{idProducto}/sectores/{idSectorEconomico}")
    public ResponseEntity<Void> asociarSector(@PathVariable Long idProducto,
                                               @PathVariable Integer idSectorEconomico) {
        productoService.asociarSector(idProducto, idSectorEconomico);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{idProducto}/sectores/{idSectorEconomico}")
    public ResponseEntity<Void> desasociarSector(@PathVariable Long idProducto,
                                                  @PathVariable Integer idSectorEconomico) {
        productoService.desasociarSector(idProducto, idSectorEconomico);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Proveedores del producto
    // ------------------------------------------------------------------

    @PostMapping("/{idProducto}/proveedores")
    public ResponseEntity<Void> asociarProveedor(@PathVariable Long idProducto,
                                                  @Valid @RequestBody ProductoProveedorRequest request) {
        productoService.asociarProveedor(idProducto, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
