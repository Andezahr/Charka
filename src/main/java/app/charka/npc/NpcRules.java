package app.charka.npc;

import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.npc.NpcSheet;
import app.charka.npc.NpcTemplate;
import java.util.List;

/**
 * NPC: готовые шаблоны и бросок листа по шаблону
 * <p>
 * ARC-01: часть правил системы ({@code GameSystem#npcs()}); каждый метод по умолчанию — «у системы этого нет».
 */
public interface NpcRules {

    /** Система без этой части */
    NpcRules NONE = new NpcRules() {
    };

    /**
     * Шаблоны быстрого создания NPC; пусто — только пустой NPC.
     */
    default List<NpcTemplate> getNpcTemplates() {
        return List.of();
    }

    /**
     * Выбросить NPC по шаблону.
     *
     * @param advanced продвинутый NPC, если система это различает
     * @throws IllegalArgumentException неизвестный шаблон
     */
    default NpcSheet rollNpc(String templateCode, boolean advanced, DiceRoller dice) {
        throw new IllegalArgumentException("Нет такого шаблона NPC: " + templateCode);
    }
}
