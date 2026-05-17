package com.projectpulse.api;

import com.projectpulse.reporting.ReportGenerationException;
import com.projectpulse.scanner.InvalidScanRootException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidScanRootException.class)
    public ResponseEntity<ApiError> handleInvalidScanRoot(InvalidScanRootException exception) {
        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        ));
    }

    @ExceptionHandler(ReportGenerationException.class)
    public ResponseEntity<ApiError> handleReportGeneration(ReportGenerationException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                exception.getMessage()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .toList();

        return ResponseEntity.badRequest().body(new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Request validation failed.",
                details
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected server error."
        ));
    }

    private String formatFieldError(FieldError error) {
        return "%s: %s".formatted(error.getField(), error.getDefaultMessage());
    }

    public record ApiError(
            Instant timestamp,
            int status,
            String error,
            String message,
            List<String> details
    ) {
        static ApiError of(HttpStatus status, String message) {
            return new ApiError(
                    Instant.now(),
                    status.value(),
                    status.getReasonPhrase(),
                    message,
                    List.of()
            );
        }
    }
}
