package app.charka.system.kosmozeki;

import app.charka.npc.NpcSheet;
import app.charka.npc.NpcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NPC и звери (K-12): готовых противников в системе нет — мастер задаёт каждого сам. Шаблон даёт только набор зон тела,
 * дальше мастер правит ХП, броню и зоны в карточке тела.
 */
final class KzNpcs {

    private KzNpcs() {
    }

    static List<NpcTemplate> templates() {
        return KzBody.PRESETS.stream().map(preset -> new NpcTemplate(preset.code(), preset.label())).toList();
    }

    /**
     * @throws IllegalArgumentException неизвестный набор
     */
    static NpcSheet sheet(String code) {
        KzBody.Preset preset = KzBody.preset(code).orElseThrow(() -> new IllegalArgumentException("Нет такого шаблона NPC: " + code));
        return new NpcSheet(Set.of(), Map.of(), Map.of(), List.of(),
                Map.of(KzBodyStrategy.TYPE, Map.of("preset", preset.code())));
    }
}
