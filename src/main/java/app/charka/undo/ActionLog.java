package app.charka.undo;

import app.charka.model.AppUser;
import app.charka.security.AccessService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.Map;
import java.util.Set;

/**
 * LOG-01: одна строка журнала на изменяющее действие кампании или персонажа — кто, где, что и чем кончилось.
 * Разбор «что-то не так за столом»: {@code grep campaign=7} в журнале сервера.
 * <p>
 * Пишется только адрес и итог: ни параметров (пароли, тексты), ни приглашений ({@code /join} сюда не входит).
 * Отказ хода и конфликт версий — WARN, остальные отказы и успех — INFO; ошибки 500 со стеком пишут обработчики исключений.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionLog implements HandlerInterceptor {

    /** Отказ, который увидел игрок: кладут обработчики исключений; отказы контроллеров берутся из flash */
    public static final String REFUSAL = ActionLog.class.getName() + ".refusal";
    /** Отказ, который стоит заметить: не ваш ход, конфликт версий */
    public static final String WARN = ActionLog.class.getName() + ".warn";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final AccessService accessService;

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler,
                                Exception ex) {
        if (SAFE_METHODS.contains(request.getMethod()) || request.getDispatcherType() != DispatcherType.REQUEST || !log.isInfoEnabled()) {
            return;
        }
        String user = accessService.current().map(AppUser::getUsername).orElse("-");
        Object campaign = request.getAttribute(CampaignLock.CAMPAIGN_ID);
        String line = "campaign=" + (campaign == null ? "-" : campaign) + " user=" + user + " " + request.getMethod() + " "
                + request.getRequestURI() + " -> " + response.getStatus();
        String refusal = refusal(request);
        if (request.getAttribute(WARN) != null) {
            log.warn("{} отказ: {}", line, refusal);
        } else if (refusal != null) {
            log.info("{} отказ: {}", line, refusal);
        } else {
            log.info(line);
        }
    }

    private static String refusal(HttpServletRequest request) {
        if (request.getAttribute(REFUSAL) instanceof String message) {
            return message;
        }
        FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
        if (flash == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : flash.entrySet()) {
            if ((entry.getKey().endsWith("Error") || entry.getKey().equals("errorMessage")) && entry.getValue() != null) {
                return String.valueOf(entry.getValue());
            }
        }
        return null;
    }
}
