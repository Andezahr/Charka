package app.charka.system;

import app.charka.model.Campaign;
import app.charka.model.Character;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Все зарегистрированные игровые системы по коду (как {@code CounterStrategyFactory} для счётчиков).
 */
@Component
public class GameSystemRegistry {

    private final Map<String, GameSystem> systems;

    public GameSystemRegistry(List<GameSystem> systemList) {
        this.systems = systemList.stream()
                .collect(Collectors.toMap(GameSystem::getCode, Function.identity()));
    }

    /**
     * @throws IllegalArgumentException если система с таким кодом не зарегистрирована
     */
    public GameSystem get(String code) {
        return find(code)
                .orElseThrow(() -> new IllegalArgumentException("Неизвестная игровая система: " + code));
    }

    public Optional<GameSystem> find(String code) {
        return Optional.ofNullable(code).map(systems::get);
    }

    /**
     * Система персонажа; неизвестный код (например, удалённой системы) — универсальная, чтобы страницы не падали.
     */
    public GameSystem forCharacter(Character character) {
        return find(systemCodeOf(character)).orElseGet(this::getGeneric);
    }

    public GameSystem getGeneric() {
        return get(GenericSystem.CODE);
    }

    /**
     * Код → название, для списков, где у каждой строки своя система.
     */
    public Map<String, String> getDisplayNames() {
        return systems.values().stream()
                .collect(Collectors.toMap(GameSystem::getCode, GameSystem::getDisplayName));
    }

    /**
     * Все системы, отсортированные по названию — для формы создания кампании.
     */
    public List<GameSystem> getAll() {
        return systems.values().stream()
                .sorted(Comparator.comparing(GameSystem::getDisplayName))
                .toList();
    }

    /**
     * Код системы персонажа: система его кампании, персонаж без кампании — универсальная.
     * Статический, чтобы им могли пользоваться сервисы, от которых зависят сами системы (без циклов бинов).
     */
    public static String systemCodeOf(Character character) {
        Campaign campaign = character == null ? null : character.getCampaign();
        if (campaign == null || campaign.getSystem() == null) {
            return GenericSystem.CODE;
        }
        return campaign.getSystem();
    }
}
