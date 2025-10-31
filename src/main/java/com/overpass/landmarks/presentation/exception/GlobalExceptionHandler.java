package com.overpass.landmarks.presentation.exception;

import com.overpass.landmarks.application.service.WebhookService;
import com.overpass.landmarks.infrastructure.external.OverpassClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler providing consistent JSON error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        logger.debug("Validation error", ex);
        
        Map<String, Object> error = new HashMap<>();
        error.put("error", "VALIDATION_ERROR");
        
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError -> {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        });
        error.put("fieldErrors", fieldErrors);
        error.put("message", "Validation failed");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        logger.debug("Type mismatch error", ex);
        
        Map<String, Object> error = new HashMap<>();
        error.put("error", "INVALID_PARAMETER");
        error.put("message", "Invalid parameter type: " + ex.getName());
        error.put("parameter", ex.getName());
        error.put("expectedType", ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        logger.debug("Illegal argument error", ex);
        
        Map<String, Object> error = new HashMap<>();
        error.put("error", "INVALID_INPUT");
        error.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(OverpassClient.OverpassException.class)
    public ResponseEntity<Map<String, Object>> handleOverpassException(OverpassClient.OverpassException ex) {
        logger.error("Overpass API error", ex);
        
        Map<String, Object> error = new HashMap<>();
        error.put("error", "OVERPASS_ERROR");
        error.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }

    @ExceptionHandler(WebhookService.WebhookProcessingException.class)
    public ResponseEntity<Map<String, Object>> handleWebhookProcessingException(WebhookService.WebhookProcessingException ex) {
        logger.error("Webhook processing error", ex);
        
        Map<String, Object> error = new HashMap<>();
        error.put("error", "WEBHOOK_PROCESSING_ERROR");
        error.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        logger.error("Unexpected error", ex);
        
        Map<String, Object> error = new HashMap<>();
        error.put("error", "INTERNAL_ERROR");
        error.put("message", "An unexpected error occurred");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

