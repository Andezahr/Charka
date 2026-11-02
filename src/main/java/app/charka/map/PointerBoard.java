package app.charka.map;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Указатели на карте боя (M-13): «смотрите сюда», «бегу сюда». Не пишутся в БД — живут в памяти несколько секунд
 * и приходят в JSON карты, поэтому доходят до других устройств обычным опросом.
 */
@Component
public class PointerBoard {

    public static final long LIFETIME_MS = 5000;
    private static final int KEEP = 20;

    /**
     * @param who     кто показал: имя персонажа или «Мастер»
     * @param fromX   начало стрелки — своя фишка; null — просто кольцо
     * @param created когда, мс
     */
    public record Pointer(long id, int x, int y, Integer fromX, Integer fromY, String who, long created) {
    }

    private final Map<Long, Deque<Pointer>> byCampaign = new ConcurrentHashMap<>();
    private final Clock clock;
    private long sequence;

    public PointerBoard() {
        this(Clock.systemUTC());
    }

    PointerBoard(Clock clock) {
        this.clock = clock;
    }

    public synchronized Pointer add(Long campaignId, int x, int y, Integer fromX, Integer fromY, String who) {
        Deque<Pointer> pointers = byCampaign.computeIfAbsent(campaignId, id -> new ArrayDeque<>());
        Pointer pointer = new Pointer(++sequence, x, y, fromX, fromY, who == null || who.isBlank() ? "Мастер" : who.strip(), clock.millis());
        pointers.addLast(pointer);
        while (pointers.size() > KEEP) {
            pointers.removeFirst();
        }
        return pointer;
    }

    /** Живые указатели; устаревшие уходят */
    public synchronized List<Pointer> live(Long campaignId) {
        Deque<Pointer> pointers = byCampaign.get(campaignId);
        if (pointers == null) {
            return List.of();
        }
        long now = clock.millis();
        pointers.removeIf(pointer -> now - pointer.created() > LIFETIME_MS);
        return List.copyOf(pointers);
    }

    public synchronized void clear(Long campaignId) {
        byCampaign.remove(campaignId);
    }
}
