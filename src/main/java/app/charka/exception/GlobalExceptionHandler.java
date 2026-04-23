package app.charka.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public record ErrorResponse(
            LocalDateTime timestamp,
            String message,
            String details
    ) {}

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex, WebRequest request) {
        return buildResponse(ex, request, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> buildResponse(Exception ex, WebRequest request, HttpStatus status) {
        String details = request.getDescription(false);
        if (details.startsWith("uri=")) {
            details = details.substring(4);
        }
        String message = ex.getMessage() != null ? ex.getMessage() : status.getReasonPhrase();
        return new ResponseEntity<>(new ErrorResponse(LocalDateTime.now(), message, details), status);
    }
}