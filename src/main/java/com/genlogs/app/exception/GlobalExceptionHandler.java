package com.genlogs.app.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> noEncontrado(ResourceNotFoundException ex) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> negocio(BusinessException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> rutaInexistente(NoResourceFoundException ex) {
        return respuesta(HttpStatus.NOT_FOUND, "La ruta /" + ex.getResourcePath() + " no existe");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> metodoNoSoportado(HttpRequestMethodNotSupportedException ex) {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "El método " + ex.getMethod() + " no está soportado aquí");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> accesoDenegado(AccessDeniedException ex) {
        return respuesta(HttpStatus.FORBIDDEN, "No tienes permiso para esta acción");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
                       MissingServletRequestParameterException.class,
                       MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> peticionInvalida(Exception ex) {
        return respuesta(HttpStatus.BAD_REQUEST, "Petición mal formada o parámetros inválidos");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> archivoGrande(MaxUploadSizeExceededException ex) {
        return respuesta(HttpStatus.CONTENT_TOO_LARGE, "El archivo supera el tamaño máximo permitido");
    }

    /** Restricciones únicas o llaves foráneas de la BD (por ejemplo, dos altas simultáneas). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integridad(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        return respuesta(HttpStatus.CONFLICT, "La operación viola una restricción de datos (registro duplicado o en uso)");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> detalles = new LinkedHashMap<>();
        for (FieldError e : ex.getBindingResult().getFieldErrors()) {
            detalles.putIfAbsent(e.getField(), e.getDefaultMessage());
        }
        Map<String, Object> body = cuerpo(HttpStatus.BAD_REQUEST, "Error de validación");
        body.put("detalles", detalles);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> generico(Exception ex) {
        log.error("Error no controlado", ex);   // el detalle queda solo en el log
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado");
    }

    private ResponseEntity<Map<String, Object>> respuesta(HttpStatus status, String mensaje) {
        return ResponseEntity.status(status).body(cuerpo(status, mensaje));
    }

    private Map<String, Object> cuerpo(HttpStatus status, String mensaje) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", mensaje);
        return body;
    }
}