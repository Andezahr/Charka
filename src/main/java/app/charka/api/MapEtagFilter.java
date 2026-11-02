package app.charka.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import java.util.regex.Pattern;

/**
 * ETag только у опрашиваемых адресов: JSON карты боя (M-03) и лента чата (U-06): опрос раз в секунду получает 304, пока ничего не изменилось, — а изменение
 * откуда угодно (атака с листа, урон, следующий ход) меняет содержимое и ловится само.
 */
@Component
public class MapEtagFilter extends ShallowEtagHeaderFilter {

    private static final Pattern MAP = Pattern.compile("^/campaign/\\d+/(encounter/map|chat)$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod()) || !MAP.matcher(request.getRequestURI()).matches();
    }
}
