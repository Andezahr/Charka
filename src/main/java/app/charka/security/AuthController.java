package app.charka.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
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
    private final InviteService inviteService;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    /** Пришли по приглашению (A-02) — сверху название кампании, регистрация в той же форме */
    @GetMapping("/login")
    public String login(HttpServletRequest request, HttpServletResponse response, Model model) {
        String token = inviteToken(request, response);
        if (token != null) {
            try {
                model.addAttribute("inviteCampaign", inviteService.campaign(token).getName());
            } catch (java.util.NoSuchElementException ignored) {
                // ссылку сбросили — обычный вход
            }
        }
        return "login";
    }

    private String inviteToken(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved == null) {
            return null;
        }
        java.util.regex.Matcher join = JOIN.matcher(java.net.URI.create(saved.getRedirectUrl()).getPath());
        return join.matches() ? join.group(1) : null;
    }

    private static final java.util.regex.Pattern JOIN = java.util.regex.Pattern.compile("^/join/([A-Za-z0-9_-]+)$");

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    /**
     * @param repeat пароль ещё раз; нет — регистрация со страницы входа (A-02: один ввод пароля)
     */
    @PostMapping("/register")
    public String register(@RequestParam String username, @RequestParam String password, @RequestParam(required = false) String repeat,
                           HttpServletRequest request, HttpServletResponse response, Model model) {
        model.addAttribute("username", username);
        if (repeat != null && !password.equals(repeat)) {
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
        // пришли по ссылке-приглашению — обратно к ней
        String token = inviteToken(request, response);
        if (token != null) {
            requestCache.removeRequest(request, response);
            return "redirect:/join/" + token;
        }
        return "redirect:/";
    }
}
