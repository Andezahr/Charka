package app.charka.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
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
    public Object handleAll(Exception ex, HttpServletRequest request) {
        String accept = request.getHeader("Accept");

        if (accept == null || accept.contains("text/html")) {
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, ex);
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
            return "forward:/error";
        }

        return buildResponse(ex, new ServletWebRequest(request));
    }

    private ResponseEntity<ErrorResponse> buildResponse(Exception ex, WebRequest request) {
        String details = request.getDescription(false);
        if (details.startsWith("uri=")) {
            details = details.substring(4);
        }
        String message = ex.getMessage() != null ? ex.getMessage() : HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase();
        return new ResponseEntity<>(new ErrorResponse(LocalDateTime.now(), message, details), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

