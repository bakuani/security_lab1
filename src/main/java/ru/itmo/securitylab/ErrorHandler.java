package ru.itmo.securitylab;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ErrorHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalidInput(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError("Invalid request"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        String message = switch (status) {
            case 401 -> "Invalid credentials";
            case 404 -> "Task not found";
            default -> "Invalid request";
        };
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
