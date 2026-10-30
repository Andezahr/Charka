package app.charka.system.cp2020;

import app.charka.item.ItemField;
import app.charka.npc.NpcSheet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Быстрые NPC CP2020 (с. 30)")
class Cp2020NpcsTest {

    /** 18 кубиков статов (по 2 на стат), затем кубики снаряжения */
    private static Deque<Integer> rolls(int statDie, Integer... rest) {
        List<Integer> all = new ArrayList<>(Collections.nCopies(18, statDie));
        all.addAll(Arrays.asList(rest));
        return new ArrayDeque<>(all);
    }

    @Test
    @DisplayName("Пакет шаблона — 10 навыков каталога, в сумме 40 очков (с. 44)")
    void careerPackages() {
        assertThat(Cp2020Npcs.TEMPLATES).allSatisfy(template -> {
            assertThat(template.career()).hasSize(10);
            assertThat(template.career().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(40);
            assertThat(template.career().keySet()).allMatch(code -> Cp2020Skills.CATALOG.stream().anyMatch(skill -> skill.code().equals(code)));
            assertThat(template.pickups()).hasSize(5).doesNotContainAnyElementsOf(template.career().keySet())
                    .allMatch(code -> Cp2020Skills.CATALOG.stream().anyMatch(skill -> skill.code().equals(code)));
            assertThat(template.statOrder()).hasSize(9).doesNotHaveDuplicates();
            assertThat(Cp2020Skills.ROLES).anyMatch(role -> role.code().equals(template.role()));
        });
    }

    @Test
    @DisplayName("Статы: 2D6, 11 и выше перебрасывается, лучшее — первому стату шаблона")
    void stats() {
        // первый стат: 6 + 6 = 12 — переброс, затем 5 + 5 = 10; семь раз 2 + 2; последний 1 + 1; кубик снаряжения
        Deque<Integer> queue = new ArrayDeque<>(List.of(6, 6, 5, 5, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1));
        Map<String, Integer> stats = Cp2020Npcs.roll("bandit", false, sides -> queue.removeFirst())
                .attributes().get(Cp2020StatsStrategy.TYPE);

        assertThat(stats).hasSize(9).containsEntry("ref", 10).containsEntry("attr", 2);
    }

    @ParameterizedTest
    @CsvSource({
            "bandit, 1, armor_heavy_leather, knife",
            "bandit, 10, armor_metalgear, fn_ral_heavy_assault_rifle",
            "cop, 1, armor_light_jacket, militech_arms_avenger",
            "merc, 1, armor_light_jacket, sternmeyer_type_35",
            "merc, 9, armor_metalgear, fn_ral_heavy_assault_rifle"
    })
    @DisplayName("Снаряжение: один бросок на броню и оружие с прибавкой роли, не выше 10-й строки")
    void gear(String template, int roll, String armor, String weapon) {
        Deque<Integer> queue = rolls(1, roll);
        NpcSheet sheet = Cp2020Npcs.roll(template, false, sides -> queue.removeFirst());

        assertThat(sheet.items()).containsExactly(new NpcSheet.Gear(armor, "WEAR"), new NpcSheet.Gear(weapon, null));
    }

    @Test
    @DisplayName("Снаряжение таблицы есть в справочнике")
    void gearInCatalog() {
        for (int roll = 1; roll <= 10; roll++) {
            Deque<Integer> queue = rolls(1, roll);
            NpcSheet sheet = Cp2020Npcs.roll("bandit", false, sides -> queue.removeFirst());
            assertThat(sheet.items()).allSatisfy(gear -> assertThat(
                    java.util.stream.Stream.concat(Cp2020Weapons.CATALOG.stream(), Cp2020Armors.CATALOG.stream()))
                    .anyMatch(row -> row.key().equals(gear.catalogKey())));
        }
    }

    @Test
    @DisplayName("Обычный NPC: только пакет; продвинутый: ещё 2D10 на 5 других навыков")
    void advanced() {
        Deque<Integer> plain = rolls(1, 5);
        assertThat(Cp2020Npcs.roll("booster", false, sides -> plain.removeFirst()).skills()).hasSize(10);

        // после статов 2D10 навыков (7 + 6 = 13), потом снаряжение
        Deque<Integer> queue = rolls(1, 7, 6, 5);
        Map<String, Integer> skills = Cp2020Npcs.roll("booster", true, sides -> queue.removeFirst()).skills();

        assertThat(skills).hasSize(15).containsEntry("motorcycle", 3).containsEntry("hide_evade", 2);
    }

    @Test
    @DisplayName("Неизвестный шаблон — ошибка")
    void unknown() {
        assertThatThrownBy(() -> Cp2020Npcs.roll("dragon", false, sides -> 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
