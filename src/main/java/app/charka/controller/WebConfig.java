package app.charka.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Перехватчики MVC: права пользователя на кампанию и персонажа, принадлежность ресурса из адреса персонажу, снимок для отмены.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final OwnershipInterceptor ownershipInterceptor;
    private final app.charka.security.AccessInterceptor accessInterceptor;
    private final app.charka.security.TurnGuard turnGuard;
    private final app.charka.undo.CampaignLock campaignLock;
    private final app.charka.undo.UndoInterceptor undoInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // сначала права пользователя (U-03), потом принадлежность ресурсов персонажу (U-02)
        registry.addInterceptor(accessInterceptor).addPathPatterns("/campaign/**", "/character/**", "/api/**");
        // VER-01: изменения одной кампании — по одному, до снимка отмены и до проверки хода
        registry.addInterceptor(campaignLock).addPathPatterns("/campaign/**", "/character/**", "/api/**");
        // TURN-01: в бою вне своего хода игрок меняет только разрешённое
        registry.addInterceptor(turnGuard).addPathPatterns("/campaign/**", "/character/**", "/api/**");
        registry.addInterceptor(ownershipInterceptor).addPathPatterns("/character/**");
        // A-01: снимок для отмены — только для разрешённых запросов
        registry.addInterceptor(undoInterceptor).addPathPatterns("/campaign/**", "/character/**");
        // R-08: действие с вкладки «Снаряжение» возвращает на неё, а не на лист
        registry.addInterceptor(new GearReturnInterceptor()).addPathPatterns("/character/**");
    }
}
