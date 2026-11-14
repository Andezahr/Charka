package app.charka.system.kosmozeki;

import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.npc.NpcTemplate;
import java.util.List;
import app.charka.npc.NpcRules;

/**
 * ARC-01: nPC: готовые шаблоны и бросок листа по шаблону — правила этой системы (вынесено из KzSystem).
 */
class KzNpcRules implements NpcRules {

    @Override
    public List<NpcTemplate> getNpcTemplates() {
        return KzNpcs.templates();
    }

    @Override
    public app.charka.npc.NpcSheet rollNpc(String templateCode, boolean advanced, DiceRoller dice) {
        return KzNpcs.sheet(templateCode);
    }
}
