package app.charka.undo;

import app.charka.repository.CharacterRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VER-01: изменения одной кампании — по одному. Замок берётся до снимка отмены ({@link UndoInterceptor}) и отпускается
 * после записи отмены и ответа: снимок «до», действие и «после» неразрывны, два урона одной цели разом не теряются.
 * <p>
 * Замок в памяти — приложение работает в одном экземпляре. Чтение (GET, опрос) замок не берёт. Ждать не дольше
 * {@link #WAIT_SECONDS} с, потом 409.
 */
@Component
@RequiredArgsConstructor
public class CampaignLock implements HandlerInterceptor {

    static final long WAIT_SECONDS = 10;
    public static final String BUSY = "Сервер занят другим действием этой кампании — повторите";

    private static final Pattern CAMPAIGN = Pattern.compile("^/(?:api/)?campaigns?/(\\d+)(?:/.*)?$");
    private static final Pattern CHARACTER = Pattern.compile("^/(?:api/)?characters?/(\\d+)(?:/.*)?$");
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String HELD = CampaignLock.class.getName() + ".held";

    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final CharacterRepository characterRepository;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler)
            throws InterruptedException {
        // forward (K-09 переброс) идёт под замком исходного запроса
        if (SAFE_METHODS.contains(request.getMethod()) || request.getDispatcherType() != jakarta.servlet.DispatcherType.REQUEST) {
            return true;
        }
        Long campaignId = campaignOf(request.getRequestURI().substring(request.getContextPath().length()));
        if (campaignId == null) {
            return true;
        }
        ReentrantLock lock = locks.computeIfAbsent(campaignId, id -> new ReentrantLock(true));
        if (!lock.tryLock(WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, BUSY);
        }
        request.setAttribute(HELD, lock);
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler,
                                Exception ex) {
        if (request.getDispatcherType() == jakarta.servlet.DispatcherType.REQUEST
                && request.getAttribute(HELD) instanceof ReentrantLock lock && lock.isHeldByCurrentThread()) {
            request.removeAttribute(HELD);
            lock.unlock();
        }
    }

    private Long campaignOf(String path) {
        Matcher campaign = CAMPAIGN.matcher(path);
        if (campaign.matches()) {
            return Long.valueOf(campaign.group(1));
        }
        Matcher character = CHARACTER.matcher(path);
        if (character.matches()) {
            return characterRepository.findById(Long.valueOf(character.group(1)))
                    .map(found -> found.getCampaign() == null ? null : found.getCampaign().getId())
                    .orElse(null);
        }
        return null;
    }
}
