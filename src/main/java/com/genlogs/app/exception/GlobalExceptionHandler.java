package com.genlogs.app.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.ServletRequestBindingException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Ruta inexistente: 404 en lugar de caer en el handler genérico (500). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRutaInexistente(NoResourceFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "La ruta /" + ex.getResourcePath() + " no existe");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Acceso denegado: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "No tienes permisos suficientes para realizar esta acción.");
    }

    /** JSON mal formado, parámetro faltante o de tipo incorrecto: 400 en lugar de 500. */
    @ExceptionHandler({HttpMessageNotReadableException.class,
                       MissingServletRequestParameterException.class,
                       MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handlePeticionInvalida(Exception ex) {
        log.warn("Petición inválida: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Petición mal formada o parámetros inválidos.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Archivo supera tamaño máximo permitido: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONTENT_TOO_LARGE, "El archivo supera el tamaño máximo permitido (20MB).");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP no permitido: " + ex.getMethod());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Conflicto de integridad de datos", ex);
        return buildResponse(HttpStatus.CONFLICT, "La operación no se pudo completar debido a un conflicto o restricción en los datos.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        // LinkedHashMap + putIfAbsent: se conserva el orden de los campos y el primer mensaje de cada uno.
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        String primerMensaje = errores.values().stream().findFirst().orElse("Error de validación");

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Error de validación");
        body.put("message", primerMensaje);
        body.put("detalles", errores);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Validación de parámetros del método del controller (Spring 6.1+): si no
     * se atiende aquí, el handler genérico la degradaba a 500.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidacionDeParametros(HandlerMethodValidationException ex) {
        log.warn("Parámetros inválidos: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Parámetros de la petición inválidos.");
    }

    /** ConstraintViolationException (validación jakarta fuera del binding): 400, no 500. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Violación de restricción: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Parámetros de la petición inválidos.");
    }

    /** Sin cabecera obligatoria o sin variable de ruta: 400, no 500. */
    @ExceptionHandler({MissingRequestHeaderException.class,
                       MissingPathVariableException.class,
                       ServletRequestBindingException.class})
    public ResponseEntity<Map<String, Object>> handlePeticionIncompleta(Exception ex) {
        log.warn("Petición incompleta: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Faltan datos obligatorios en la petición.");
    }

    /** Content-Type no soportado: 415, no 500. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNoSoportado(HttpMediaTypeNotSupportedException ex) {
        log.warn("Content-Type no soportado: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El tipo de contenido de la petición no está soportado.");
    }

    /** Sin credenciales: 401, no 500. */
    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSinCredenciales(AuthenticationCredentialsNotFoundException ex) {
        log.warn("Petición sin autenticación: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión para realizar esta acción.");
    }

    /**
     * Falta de conexiones o timeout (típico con el pooler de Supabase): 503
     * en lugar del 500 genérico, para que el frontend pueda reintentar.
     */
    @ExceptionHandler({CannotAcquireLockException.class,
                       QueryTimeoutException.class,
                       CannotGetJdbcConnectionException.class})
    public ResponseEntity<Map<String, Object>> handleBaseDeDatosNoDisponible(Exception ex) {
        log.error("Base de datos no disponible temporalmente", ex);
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE,
                "La base de datos no está disponible en este momento. Intenta de nuevo en unos segundos.");
    }

    /** Conflicto de bloqueo optimista / pesimista: 409, no 500. */
    @ExceptionHandler({PessimisticLockingFailureException.class,
                       OptimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> handleConflictoDeBloqueo(Exception ex) {
        log.error("Conflicto de bloqueo al guardar los datos", ex);
        return buildResponse(HttpStatus.CONFLICT,
                "El registro fue modificado por otra operación. Recarga la página e inténtalo de nuevo.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        // El detalle queda solo en el log de Render; al cliente no se le expone.
        log.error("Error inesperado en el servidor", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado en el servidor. Por favor, intente nuevamente.");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String mensaje) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", mensaje);
        // El frontend lee "message"; se mantiene "error" por compatibilidad.
        body.put("message", mensaje);
        return ResponseEntity.status(status).body(body);
    }
}

