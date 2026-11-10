package app.charka.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ошибки скрипта у игроков (A-14): app.js присылает их сюда, в журнал сервера — по ним ищется, отчего «приложение
 * перестаёт отвечать» на чьём-то телефоне. Ничего не хранится.
 */
@Controller
@RequiredArgsConstructor
public class ClientErrorController {

    private static final Logger LOG = LoggerFactory.getLogger("app.charka.client");

    private final app.charka.security.AccessService accessService;

    @PostMapping("/client-error")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@RequestParam(defaultValue = "") String page, @RequestParam(defaultValue = "") String message,
                       @RequestParam(defaultValue = "") String stack) {
        String who = accessService.current().map(app.charka.model.AppUser::getUsername).orElse("-");
        LOG.warn("Ошибка скрипта у {} на {}: {}\n{}", who, cut(page, 300), cut(message, 500), cut(stack, 2000));
    }

    private static String cut(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }
}
