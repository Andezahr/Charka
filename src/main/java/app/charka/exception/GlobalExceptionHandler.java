package app.charka.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    /**
     * TURN-01: не свой ход — назад, откуда нажали, с подписью над боем и на листе. Адрес — только путь этого сайта:
     * {@code returnCampaign} (атака из трекера) → бой, иначе путь из Referer, иначе лист или бой по адресу запроса.
     */
    @ExceptionHandler(NotYourTurnException.class)
    public ModelAndView handleNotYourTurn(NotYourTurnException ex, jakarta.servlet.http.HttpServletRequest request) {
        request.setAttribute(app.charka.undo.ActionLog.WARN, true);
        request.setAttribute(app.charka.undo.ActionLog.REFUSAL, ex.getMessage());
        org.springframework.web.servlet.FlashMap flash = org.springframework.web.servlet.support.RequestContextUtils.getOutputFlashMap(request);
        String target = backTo(request);
        // подпись там, где её показывает страница: бой — encounterError, лист и прочее — errorMessage
        flash.put(target.contains("/encounter") ? "encounterError" : "errorMessage", ex.getMessage());
        return new ModelAndView("redirect:" + target);
    }

    /** VER-01: пока игрок нажимал, ту же строку изменил другой — сообщение и назад, а не 500 */
    public static final String CONFLICT = "Пока вы нажимали, состояние изменилось — посмотрите и повторите";

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ModelAndView handleConflict(org.springframework.dao.OptimisticLockingFailureException ex,
                                       jakarta.servlet.http.HttpServletRequest request) {
        log.warn("Конфликт версий: {} {} — {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        request.setAttribute(app.charka.undo.ActionLog.WARN, true);
        request.setAttribute(app.charka.undo.ActionLog.REFUSAL, CONFLICT);
        String target = backTo(request);
        org.springframework.web.servlet.support.RequestContextUtils.getOutputFlashMap(request)
                .put(target.contains("/encounter") ? "encounterError" : "errorMessage", CONFLICT);
        ModelAndView redirect = new ModelAndView("redirect:" + target);
        return redirect;
    }

    static String backTo(jakarta.servlet.http.HttpServletRequest request) {
        String campaign = request.getParameter("returnCampaign");
        if (campaign != null && campaign.matches("\\d+")) {
            return "/campaign/" + campaign + "/encounter";
        }
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                java.net.URI uri = java.net.URI.create(referer);
                if (uri.getPath() != null && uri.getPath().startsWith("/") && !uri.getPath().startsWith("//")) {
                    return uri.getPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                }
            } catch (IllegalArgumentException ignored) {
                // кривой Referer — по адресу запроса
            }
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^/(campaign|character)/(\\d+)").matcher(path);
        if (matcher.find()) {
            return "/" + matcher.group(1) + "/" + matcher.group(2) + (matcher.group(1).equals("campaign") ? "/encounter" : "");
        }
        return "/";
    }

    /** Некорректные данные запроса → 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ModelAndView handleIllegalArgument(IllegalArgumentException ex, jakarta.servlet.http.HttpServletRequest request) {
        request.setAttribute(app.charka.undo.ActionLog.REFUSAL, ex.getMessage());
        return errorPage(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Параметр запроса не того типа (буквы в числовом поле) → 400, а не 500 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return errorPage(HttpStatus.BAD_REQUEST, "Некорректное значение параметра «" + ex.getName() + "»");
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
