package app.charka.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import java.util.NoSuchElementException;

/**
 * Ошибки страниц (MVC-контроллеры с шаблонами): показывает {@code error.html} с нужным HTTP-статусом.
 * REST-эндпоинты из {@code app.charka.api} обрабатывает {@link ApiExceptionHandler}.
 */
@Slf4j
@ControllerAdvice(annotations = Controller.class)
public class GlobalExceptionHandler {

    static final String ERROR_VIEW = "error";

    /** {@link NotFoundException} и наследники (все — {@link NoSuchElementException}) → 404 */
    @ExceptionHandler(NoSuchElementException.class)
    public ModelAndView handleNotFound(NoSuchElementException ex) {
        return errorPage(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ModelAndView handleResponseStatus(ResponseStatusException ex) {
        return errorPage(ex.getStatusCode(), ex.getReason());
    }

    /** Некорректные данные запроса → 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ModelAndView handleIllegalArgument(IllegalArgumentException ex) {
        return errorPage(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ModelAndView handleRuntime(RuntimeException ex) {
        log.error("Необработанная ошибка", ex);
        return errorPage(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    private static ModelAndView errorPage(HttpStatusCode status, String message) {
        ModelAndView mav = new ModelAndView(ERROR_VIEW, status);
        mav.addObject("status", status.value());
        mav.addObject("message", message);
        return mav;
    }
}
