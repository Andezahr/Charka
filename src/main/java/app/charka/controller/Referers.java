package app.charka.controller;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;

/**
 * Куда вернуться после действия, которое нажимают с разных страниц (отмена, запрос броска): страница из Referer
 * этого же сайта, иначе запасной адрес.
 */
final class Referers {

    private Referers() {
    }

    static String back(HttpServletRequest request, String fallback) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                String path = uri.getRawPath();
                if (path != null && path.startsWith("/") && !path.startsWith("//")
                        && (uri.getHost() == null || uri.getHost().equals(request.getServerName()))) {
                    return path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                }
            } catch (IllegalArgumentException ignored) {
                // кривой заголовок — на запасной адрес
            }
        }
        return fallback;
    }
}
