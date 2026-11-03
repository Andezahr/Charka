package app.charka.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Вход по логину и паролю (U-03). Права по кампаниям проверяет {@link AccessInterceptor}, здесь — только «вошёл или нет».
 * CSRF — в cookie {@code XSRF-TOKEN}: формы несут скрытое поле, запросы app.js — заголовок {@code X-XSRF-TOKEN}.
 * {@code charka.security.enabled=false} (профиль тестов) — всё открыто, как до пользователей.
 */
@Configuration
public class SecurityConfig {

    /** «Запомнить меня» на 30 дней: телефон у стола не спрашивает пароль каждый раз */
    private static final int REMEMBER_SECONDS = (int) Duration.ofDays(30).toSeconds();

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserService userService,
                                                   @Value("${charka.security.enabled:true}") boolean enabled,
                                                   @Value("${charka.security.remember-key:charka-remember}") String rememberKey) throws Exception {
        if (!enabled) {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        // значение в заголовке — то же, что в cookie: app.js читает его как есть
        csrfHandler.setCsrfRequestAttributeName(null);
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/register", "/css/**", "/js/**", "/favicon.ico", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", false).permitAll())
                .rememberMe(remember -> remember.key(rememberKey).userDetailsService(userService)
                        .tokenValiditySeconds(REMEMBER_SECONDS).alwaysRemember(true))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).csrfTokenRequestHandler(csrfHandler))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .build();
    }

    /** Токен ленивый: без этого cookie не появится на страницах без форм, и первый запрос app.js получит 403 */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
