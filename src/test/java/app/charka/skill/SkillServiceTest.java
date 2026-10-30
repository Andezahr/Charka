package app.charka.skill;

import app.charka.check.CheckRequest;
import app.charka.check.CheckResult;
import app.charka.check.CheckService;
import app.charka.exception.CharacterNotFoundException;
import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.CharacterSkill;
import app.charka.model.counters.Counter;
import app.charka.repository.CharacterRepository;
import app.charka.system.GameSystemRegistry;
import app.charka.system.GenericSystem;
import app.charka.system.cp2020.Cp2020HealthStrategy;
import app.charka.system.cp2020.Cp2020StatsStrategy;
import app.charka.system.cp2020.Cp2020System;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SkillService — навыки и роль персонажа")
class SkillServiceTest {

    @Mock private CharacterRepository characterRepository;
    @Mock private CheckService checkService;

    private SkillService skillService;
    private Character character;

    @BeforeEach
    void setUp() {
        GameSystemRegistry registry = new GameSystemRegistry(List.of(new GenericSystem(), new Cp2020System(null)));
        skillService = new SkillService(characterRepository, registry, checkService);
        character = characterIn(Cp2020System.CODE);
        when(characterRepository.findById(1L)).thenReturn(Optional.of(character));
    }

    private static Character characterIn(String system) {
        Campaign campaign = new Campaign();
        campaign.setSystem(system);
        Character character = new Character();
        character.setId(1L);
        character.setCampaign(campaign);
        return character;
    }

    private void withStats(int ref, int damage) {
        Counter stats = new Counter();
        stats.setId(1L);
        stats.setType(Cp2020StatsStrategy.TYPE);
        stats.setCharacter(character);
        new Cp2020StatsStrategy().initialize(stats, Map.of(Cp2020StatsStrategy.REF, ref));
        Counter health = new Counter();
        health.setId(2L);
        health.setType(Cp2020HealthStrategy.TYPE);
        health.setValue(damage);
        character.getCounters().addAll(List.of(stats, health));
    }

    private CharacterSkill addSkill(Long id, String code, String customName, int level) {
        CharacterSkill skill = new CharacterSkill();
        skill.setId(id);
        skill.setCharacter(character);
        skill.setSkillCode(code);
        skill.setCustomName(customName);
        skill.setLevel(level);
        character.getSkills().add(skill);
        return skill;
    }

    private SkillSheet.Group group(SkillSheet sheet, String title) {
        return sheet.groups().stream().filter(g -> g.title().equals(title)).findFirst().orElseThrow();
    }

    private SkillSheet.Row row(SkillSheet sheet, String title, String name) {
        return group(sheet, title).rows().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }

    @Nested
    @DisplayName("Лист навыков")
    class SheetTests {

        @Test
        @DisplayName("итог = текущий стат (со штрафом ран) + уровень")
        void totalUsesEffectiveStat() {
            withStats(8, 5); // серьёзное ранение: REF 8 − 2 = 6
            addSkill(10L, "handgun", null, 4);

            SkillSheet sheet = skillService.getSheet(1L);

            SkillSheet.Row handgun = row(sheet, "REF", "Пистолеты");
            assertThat(handgun.level()).isEqualTo(4);
            assertThat(handgun.total()).isEqualTo(10);
            assertThat(group(sheet, "REF").stat().isReduced()).isTrue();
            assertThat(row(sheet, "REF", "Винтовки").total()).isEqualTo(6);
        }

        @Test
        @DisplayName("спецспособность видна только у своей роли; чужая — если уже прокачана")
        void specialByRole() {
            withStats(6, 0);
            assertThat(skillService.getSheet(1L).groups()).extracting(SkillSheet.Group::title)
                    .doesNotContain("Спецспособность");

            character.setRole("solo");
            assertThat(group(skillService.getSheet(1L), "Спецспособность").rows())
                    .extracting(SkillSheet.Row::code).containsExactly("combat_sense");

            addSkill(10L, "interface", null, 3);
            assertThat(group(skillService.getSheet(1L), "Спецспособность").rows())
                    .extracting(SkillSheet.Row::code).containsExactly("combat_sense", "interface");
        }

        @Test
        @DisplayName("навык со своим названием — строка на каждое название, без пустой «базовой»")
        void customRows() {
            withStats(6, 0);
            addSkill(10L, "language", "Русский", 8);
            addSkill(11L, "language", "Японский", 2);

            SkillSheet sheet = skillService.getSheet(1L);

            assertThat(group(sheet, "INT").rows()).extracting(SkillSheet.Row::name)
                    .contains("Язык: Русский", "Язык: Японский").doesNotContain("Язык");
            assertThat(row(sheet, "INT", "Язык: Русский").customId()).isEqualTo(10L);
            assertThat(group(sheet, "INT").customTypes()).extracting(SkillDefinition::code)
                    .containsExactly("expert", "language");
            assertThat(group(sheet, "REF").customTypes()).extracting(SkillDefinition::code)
                    .containsExactly("martial_art");
            assertThat(group(sheet, "BODY").customTypes()).isEmpty();
        }

        @Test
        @DisplayName("без счётчика характеристик итог — только уровень, лист помечен")
        void noStats() {
            addSkill(10L, "handgun", null, 4);

            SkillSheet sheet = skillService.getSheet(1L);

            assertThat(sheet.statsMissing()).isTrue();
            assertThat(row(sheet, "REF", "Пистолеты").total()).isEqualTo(4);
        }

        @Test
        @DisplayName("вкладка есть только у системы с каталогом")
        void hasCatalog() {
            assertThat(skillService.hasCatalog(1L)).isTrue();

            Character generic = characterIn(GenericSystem.CODE);
            when(characterRepository.findById(2L)).thenReturn(Optional.of(generic));
            assertThat(skillService.hasCatalog(2L)).isFalse();
            assertThat(skillService.hasCatalog(99L)).isFalse();
        }
    }

    @Nested
    @DisplayName("Уровни")
    class LevelTests {

        @Test
        @DisplayName("создаёт, меняет и по нулю удаляет строку навыка")
        void createUpdateRemove() {
            addSkill(10L, "rifle", null, 3);
            addSkill(11L, "melee", null, 5);

            skillService.updateLevels(1L, Map.of("handgun", 4, "rifle", 7, "melee", 0, "dance", 0), Map.of());

            assertThat(character.getSkills()).extracting(CharacterSkill::getSkillCode, CharacterSkill::getLevel)
                    .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("rifle", 7),
                            org.assertj.core.groups.Tuple.tuple("handgun", 4));
        }

        @Test
        @DisplayName("меняет уровень навыка со своим названием по id; ноль не удаляет")
        void customLevel() {
            CharacterSkill skill = addSkill(10L, "language", "Русский", 8);

            skillService.updateLevels(1L, Map.of(), Map.of(10L, 0));

            assertThat(skill.getLevel()).isZero();
            assertThat(character.getSkills()).hasSize(1);
        }

        @Test
        @DisplayName("уровень вне 0..10, неизвестный навык и навык со своим названием без названия — ошибка")
        void rejected() {
            assertThatThrownBy(() -> skillService.updateLevels(1L, Map.of("handgun", 11), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> skillService.updateLevels(1L, Map.of("handgun", -1), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> skillService.updateLevels(1L, Map.of("nope", 1), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> skillService.updateLevels(1L, Map.of("language", 1), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(character.getSkills()).isEmpty();
        }

        @Test
        @DisplayName("чужая строка навыка — 404")
        void foreignCustom() {
            assertThatThrownBy(() -> skillService.updateLevels(1L, Map.of(), Map.of(77L, 1)))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("персонаж без каталога: уровень не задать")
        void genericHasNoSkills() {
            Character generic = characterIn(GenericSystem.CODE);
            when(characterRepository.findById(2L)).thenReturn(Optional.of(generic));

            assertThatThrownBy(() -> skillService.updateLevels(2L, Map.of("handgun", 1), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Навыки со своим названием")
    class CustomTests {

        @Test
        @DisplayName("добавляется с обрезанным названием")
        void adds() {
            skillService.addCustom(1L, "language", "  Русский ", 8);

            CharacterSkill skill = character.getSkills().getFirst();
            assertThat(skill.getCustomName()).isEqualTo("Русский");
            assertThat(skill.getLevel()).isEqualTo(8);
            assertThat(skill.getCharacter()).isSameAs(character);
        }

        @Test
        @DisplayName("пустое название, повтор и обычный навык — ошибка")
        void rejected() {
            addSkill(10L, "language", "Русский", 8);

            assertThatThrownBy(() -> skillService.addCustom(1L, "language", "  ", 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> skillService.addCustom(1L, "language", "русский", 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> skillService.addCustom(1L, "handgun", "Глок", 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(character.getSkills()).hasSize(1);
        }

        @Test
        @DisplayName("удаляется только своя строка")
        void deletes() {
            addSkill(10L, "language", "Русский", 8);
            addSkill(11L, "language", "Японский", 2);

            skillService.deleteCustom(1L, 10L);

            assertThat(character.getSkills()).extracting(CharacterSkill::getId).containsExactly(11L);
            assertThatThrownBy(() -> skillService.deleteCustom(1L, 10L)).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Роль")
    class RoleTests {

        @Test
        @DisplayName("ставится по коду системы, пусто — снимает")
        void setsAndClears() {
            skillService.setRole(1L, "solo");
            assertThat(character.getRole()).isEqualTo("solo");

            skillService.setRole(1L, " ");
            assertThat(character.getRole()).isNull();
        }

        @Test
        @DisplayName("неизвестная роль — ошибка, прежняя остаётся")
        void unknownRole() {
            character.setRole("cop");

            assertThatThrownBy(() -> skillService.setRole(1L, "paladin"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(character.getRole()).isEqualTo("cop");
        }

        @Test
        @DisplayName("неизвестный персонаж — 404")
        void unknownCharacter() {
            when(characterRepository.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> skillService.setRole(9L, "solo"))
                    .isInstanceOf(CharacterNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Проверка")
    class CheckTests {

        private CheckRequest captured() {
            ArgumentCaptor<CheckRequest> request = ArgumentCaptor.forClass(CheckRequest.class);
            verify(checkService).checkForCharacter(eq(1L), request.capture());
            return request.getValue();
        }

        @BeforeEach
        void stubCheck() {
            when(checkService.checkForCharacter(eq(1L), any()))
                    .thenReturn(new CheckResult("d10!", List.of(), 0, null, null, false, false));
        }

        @Test
        @DisplayName("стат (со штрафом) + навык + модификатор, код навыка и сложность уходят в запрос")
        void buildsRequest() {
            withStats(8, 5);
            addSkill(10L, "handgun", null, 4);

            SkillCheck check = skillService.check(1L, "handgun", null, -1, 15);

            CheckRequest request = captured();
            assertThat(check.label()).isEqualTo("Пистолеты");
            assertThat(request.modifier()).isEqualTo(9);
            assertThat(request.difficulty()).isEqualTo(15);
            assertThat(request.skillCode()).isEqualTo("handgun");
            assertThat(request.parts()).containsExactly(new CheckRequest.Part("REF", 6),
                    new CheckRequest.Part("Пистолеты", 4), new CheckRequest.Part("Мод.", -1));
        }

        @Test
        @DisplayName("навык без уровня проверяется по одному стату")
        void zeroLevel() {
            withStats(8, 0);

            skillService.check(1L, "rifle", null, 0, null);

            assertThat(captured().modifier()).isEqualTo(8);
        }

        @Test
        @DisplayName("навык со своим названием — по id строки, подпись с названием")
        void custom() {
            withStats(6, 0);
            addSkill(10L, "martial_art", "Карате", 3);

            SkillCheck check = skillService.check(1L, null, 10L, 0, 20);

            assertThat(check.label()).isEqualTo("Боевое искусство: Карате");
            assertThat(captured().skillCode()).isEqualTo("martial_art");
        }

        @Test
        @DisplayName("неизвестный навык или тип без названия — ошибка, броска нет")
        void rejected() {
            assertThatThrownBy(() -> skillService.check(1L, "nope", null, 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> skillService.check(1L, "language", null, 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(checkService, never()).checkForCharacter(any(), any());
        }
    }
}
