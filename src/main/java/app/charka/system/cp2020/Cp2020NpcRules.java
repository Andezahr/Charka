package app.charka.system.cp2020;

import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.npc.NpcSheet;
import app.charka.npc.NpcTemplate;
import java.util.List;
import app.charka.npc.NpcRules;

/**
 * ARC-01: nPC: готовые шаблоны и бросок листа по шаблону — правила этой системы (вынесено из Cp2020System).
 */
class Cp2020NpcRules implements NpcRules {

    @Override
    public List<NpcTemplate> getNpcTemplates() {
        return Cp2020Npcs.templates();
    }

    @Override
    public NpcSheet rollNpc(String templateCode, boolean advanced, DiceRoller dice) {
        return Cp2020Npcs.roll(templateCode, advanced, dice);
    }
}
