package app.charka.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.RequestDispatcher;  // ← Changed!
import jakarta.servlet.http.HttpServletRequest;  // ← Changed!

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        Integer statusCode = null;
        if (status != null) {
            statusCode = Integer.valueOf(status.toString());
        }

        if (statusCode != null) {
            model.addAttribute("statusCode", statusCode);

            switch (statusCode) {
                case 400:
                    model.addAttribute("errorTitle", "Неправильный запрос");
                    model.addAttribute("errorMessage", "Ваш запрос содержит ошибки");
                    model.addAttribute("errorIcon", "fas fa-exclamation-triangle");
                    break;
                case 401:
                    model.addAttribute("errorTitle", "Необходима авторизация");
                    model.addAttribute("errorMessage", "Для доступа к этой странице необходимо войти в систему");
                    model.addAttribute("errorIcon", "fas fa-lock");
                    break;
                case 403:
                    model.addAttribute("errorTitle", "Доступ запрещён");
                    model.addAttribute("errorMessage", "У вас нет прав для доступа к этому ресурсу");
                    model.addAttribute("errorIcon", "fas fa-ban");
                    break;
                case 404:
                    model.addAttribute("errorTitle", "Страница не найдена");
                    model.addAttribute("errorMessage", "Запрашиваемая страница не существует");
                    model.addAttribute("errorIcon", "fas fa-search");
                    break;
                case 500:
                    model.addAttribute("errorTitle", "Внутренняя ошибка сервера");
                    model.addAttribute("errorMessage", "Произошла неожиданная ошибка на сервере");
                    model.addAttribute("errorIcon", "fas fa-cogs");
                    break;
                default:
                    model.addAttribute("errorTitle", "Произошла ошибка");
                    model.addAttribute("errorMessage", "Что-то пошло не так");
                    model.addAttribute("errorIcon", "fas fa-question-circle");
            }
        }

        model.addAttribute("requestUri", requestUri);
        model.addAttribute("timestamp", new java.util.Date());

        return "error";
    }
}