package app.charka.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {

        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        int statusCode = (status != null) ? Integer.parseInt(status.toString()) : 500;


        model.addAttribute("statusCode", statusCode);
        model.addAttribute("errorTitle", "Ошибка " + statusCode);
        model.addAttribute("errorMessage", message != null ? message : "Произошла ошибка");
        model.addAttribute("errorIcon", "fas fa-exclamation-circle"); // Универсальная иконка
        model.addAttribute("requestUri", uri);
        model.addAttribute("timestamp", LocalDateTime.now());

        return "error";
    }
}