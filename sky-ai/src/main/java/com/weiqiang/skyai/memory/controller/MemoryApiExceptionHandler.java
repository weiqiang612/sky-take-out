package com.weiqiang.skyai.memory.controller;

import com.weiqiang.skyai.memory.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Handles validation and request parsing failures for the memory API.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.weiqiang.skyai.memory.controller")
public class MemoryApiExceptionHandler {

    /**
     * Converts illegal argument failures into HTTP 400 responses.
     *
     * @param ex the failure
     * @return a structured error payload
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return badRequest(ex.getMessage());
    }

    /**
     * Converts bean validation failures into HTTP 400 responses.
     *
     * @param ex the failure
     * @return a structured error payload
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(this::fieldErrorMessage)
                .orElseGet(() -> ex.getBindingResult().getAllErrors().stream()
                        .findFirst()
                        .map(error -> error.getDefaultMessage())
                        .orElse("Request validation failed"));
        return badRequest(message);
    }

    /**
     * Converts constraint violations into HTTP 400 responses.
     *
     * @param ex the failure
     * @return a structured error payload
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return badRequest(ex.getMessage());
    }

    /**
     * Converts unreadable request bodies into HTTP 400 responses.
     *
     * @param ex the failure
     * @return a structured error payload
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return badRequest("Request body must be valid JSON");
    }

    private String fieldErrorMessage(FieldError fieldError) {
        return fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : fieldError.getField() + " is invalid";
    }

    private ResponseEntity<ApiErrorResponse> badRequest(String message) {
        String effectiveMessage = message == null || message.isBlank() ? "Request is invalid" : message;
        log.debug("memory api request rejected: {}", effectiveMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse(effectiveMessage));
    }
}
