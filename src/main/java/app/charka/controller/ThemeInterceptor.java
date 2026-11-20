package app.charka.controller;

import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PH-21: тема оформления страницы по системе кампании из адреса. Кладёт в модель {@code charkaTheme} — класс для {@code body}
 * ({@code theme-<код>} из {@code GameSystem.sheet().theme()}, пусто — {@code theme-general}). Вне кампании (главная, вход)
 * класса нет — фирменный неон из {@code :root}.
 */
@Component
@RequiredArgsConstructor
public class ThemeInterceptor implements HandlerInterceptor {

    /** Имя атрибута модели, которое читают шаблоны: {@code <body th:classappend="${charkaTheme}">} */
    public static final String ATTRIBUTE = "charkaTheme";

    private static final Pattern CAMPAIGN = Pattern.compile("^/campaign/(\\d+)(?:/.*)?$");
    private static final Pattern CHARACTER = Pattern.compile("^/character/(\\d+)(?:/.*)?$");

    private final CampaignRepository campaignRepository;
    private final CharacterRepository characterRepository;
    private final GameSystemRegistry systemRegistry;

    @Override
    public void postHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler,
                           ModelAndView modelAndView) {
        if (modelAndView == null || !modelAndView.hasView() || noPage(modelAndView)) {
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        systemCode(path).ifPresent(code -> modelAndView.addObject(ATTRIBUTE, themeClass(systemRegistry.find(code)
                .orElseGet(systemRegistry::getGeneric))));
    }

    /** Класс темы для body: {@code theme-<код>}, у системы без своей темы — {@code theme-general} */
    public static String themeClass(GameSystem system) {
        String theme = system.sheet().theme();
        return "theme-" + (theme == null || theme.isBlank() ? "general" : theme);
    }

    private Optional<String> systemCode(String path) {
        Matcher campaign = CAMPAIGN.matcher(path);
        if (campaign.matches()) {
            return campaignRepository.findSystemCode(Long.valueOf(campaign.group(1)));
        }
        Matcher character = CHARACTER.matcher(path);
        if (character.matches()) {
            return characterRepository.findSystemCode(Long.valueOf(character.group(1)));
        }
        return Optional.empty();
    }

    private static boolean noPage(ModelAndView modelAndView) {
        String name = modelAndView.getViewName();
        // фрагмент («chat :: feed») — без body, а опрос не должен ходить в БД зря
        return name != null && (name.startsWith("redirect:") || name.startsWith("forward:") || name.contains("::"));
    }
}
