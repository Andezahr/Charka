package app.charka.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Вход и регистрация (U-03). После регистрации сразу вход: второй раз вводить пароль незачем.
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username, @RequestParam String password, @RequestParam String repeat,
                           HttpServletRequest request, Model model) {
        model.addAttribute("username", username);
        if (!password.equals(repeat)) {
            model.addAttribute("error", "Пароли не совпадают");
            return "register";
        }
        try {
            userService.register(username, password);
            request.login(UserService.normalize(username), password);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        } catch (ServletException e) {
            return "redirect:/login";
        }
        return "redirect:/";
    }
}
