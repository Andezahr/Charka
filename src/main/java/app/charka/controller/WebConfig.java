package app.charka.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Перехватчики MVC: права пользователя на кампанию и персонажа, принадлежность ресурса из адреса персонажу.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final OwnershipInterceptor ownershipInterceptor;
    private final app.charka.security.AccessInterceptor accessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // сначала права пользователя (U-03), потом принадлежность ресурсов персонажу (U-02)
        registry.addInterceptor(accessInterceptor).addPathPatterns("/campaign/**", "/character/**", "/api/**");
        registry.addInterceptor(ownershipInterceptor).addPathPatterns("/character/**");
    }
}
