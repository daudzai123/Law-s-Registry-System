package com.mcit.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 🔹 Handle @Valid validation errors at request level
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    // 🔹 Handle @NotBlank, @NotNull, and other validation errors at persistence level
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
        
        for (ConstraintViolation<?> violation : violations) {
            String fieldName = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            errors.put(fieldName, message);
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    // 🔹 Handle unique constraint violations
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolation(DataIntegrityViolationException e) {
        String message = extractConstraintMessage(e);

        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "Database Constraint Violation");
        errorResponse.put("message", message);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    private String extractConstraintMessage(DataIntegrityViolationException e) {
        Throwable rootCause = e.getMostSpecificCause();
        if (rootCause != null) {
            String message = rootCause.getMessage();

            // Detect NOT NULL constraint violation
            if (message.contains("null value in column")) {
                int columnStart = message.indexOf("column \"") + 8;
                int columnEnd = message.indexOf("\"", columnStart);
                if (columnStart > 7 && columnEnd > columnStart) {
                    String fieldName = message.substring(columnStart, columnEnd).trim();
                    return "Field '" + fieldName + "' cannot be null";
                }
            }

            // Detect UNIQUE constraint violation dynamically
            if (message.contains("Key (")) {
                int keyStart = message.indexOf("Key (") + 5;
                int keyEnd = message.indexOf(")", keyStart);
                if (keyStart > 4 && keyEnd > keyStart) {
                    String fieldName = message.substring(keyStart, keyEnd).trim();
                    return "Field '" + fieldName + "' already exists";
                }
            }
        }
        return "A database constraint violation occurred";
    }

    // Handle illegal argument exceptions (e.g., trying to update restricted fields)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid update operation", "message", ex.getMessage()));
    }

    // 🔹 Handle entity not found exceptions (returns 404)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "Resource Not Found", "message", ex.getMessage()));
    }

    // 🔹 Handle generic exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception e) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "Internal Server Error");
        errorResponse.put("message", e.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
