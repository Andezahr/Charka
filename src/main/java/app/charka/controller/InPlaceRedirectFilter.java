package app.charka.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Редирект для обновления страницы на месте (app.js). fetch сам идёт по редиректу и теряет якорь адреса,
 * поэтому запросу с заголовком {@value #REQUEST_HEADER} адрес отдаётся заголовком {@value #LOCATION_HEADER},
 * а новую страницу запрашивает скрипт. Обычные переходы браузера фильтр не затрагивает.
 */
@Component
public class InPlaceRedirectFilter extends OncePerRequestFilter {

    static final String REQUEST_HEADER = "X-Charka-In-Place";
    static final String LOCATION_HEADER = "X-Charka-Location";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getHeader(REQUEST_HEADER) == null) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(request, new HttpServletResponseWrapper(response) {
            @Override
            public void sendRedirect(String location) {
                setHeader(LOCATION_HEADER, location);
                setStatus(SC_NO_CONTENT);
            }
        });
    }
}
