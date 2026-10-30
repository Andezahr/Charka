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
import app.charka.system.cp2020.Cp2020IpStrategy;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
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
        @DisplayName("спецспособности видны у всех ролей персонажа, даже на уровне 0, с названием роли")
        void specialsOfAllRoles() {
            withStats(6, 0);
            assertThat(skillService.getSheet(1L).groups()).extracting(SkillSheet.Group::title)
                    .doesNotContain("Спецспособности ролей");

            character.getRoles().addAll(List.of("netrunner", "solo"));
            SkillSheet sheet = skillService.getSheet(1L);

            assertThat(group(sheet, "Спецспособности ролей").rows())
                    .extracting(SkillSheet.Row::code, SkillSheet.Row::roleName)
                    .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("combat_sense", "Соло"),
                            org.assertj.core.groups.Tuple.tuple("interface", "Нетраннер"));
            assertThat(sheet.heldRoles()).extracting(CharacterRole::code).containsExactly("solo", "netrunner");
        }

        @Test
        @DisplayName("спецспособность снятой роли скрыта, хотя уровень сохранён")
        void specialOfRemovedRole() {
            withStats(6, 0);
            character.getRoles().add("solo");
            addSkill(10L, "interface", null, 3);

            assertThat(group(skillService.getSheet(1L), "Спецспособности ролей").rows())
                    .extracting(SkillSheet.Row::code).containsExactly("combat_sense");
            assertThat(character.getSkills()).hasSize(1);
        }

        @Test
        @DisplayName("навык со своим названием — строка на каждое название, без пустой «базовой»")
        void customRows() {
            withStats(6, 0);
            addSkill(10L, "language_slavic", "Русский", 8);
            addSkill(11L, "language_japanese", "Японский", 2);

            SkillSheet sheet = skillService.getSheet(1L);

            assertThat(group(sheet, "INT").rows()).extracting(SkillSheet.Row::name)
                    .contains("Язык: Русский", "Язык: Японский").doesNotContain("Язык");
            assertThat(row(sheet, "INT", "Язык: Русский").customId()).isEqualTo(10L);
            assertThat(group(sheet, "INT").customTypes()).extracting(SkillDefinition::code)
                    .contains("expert", "language_slavic", "language_germanic");
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
            CharacterSkill skill = addSkill(10L, "language_slavic", "Русский", 8);

            skillService.updateLevels(1L, Map.of(), Map.of(10L, 0));

            assertThat(skill.getLevel()).isZero();
            assertThat(character.getSkills()).hasSize(1);
        }

        @Test
        @DisplayName("уровень вне 0..10, неизвестный навык, навык без названия и чужая спецспособность — ошибка")
        void rejected() {
            assertThatThrownBy(() -> skillService.updateLevels(1L, Map.of("interface", 1), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> skillService.updateLevels(1L, Map.of("handgun", 11), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> skillService.updateLevels(1L, Map.of("handgun", -1), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> skillService.updateLevels(1L, Map.of("nope", 1), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> skillService.updateLevels(1L, Map.of("language_slavic", 1), Map.of()))
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
            skillService.addCustom(1L, "language_slavic", "  Русский ", 8);

            CharacterSkill skill = character.getSkills().getFirst();
            assertThat(skill.getCustomName()).isEqualTo("Русский");
            assertThat(skill.getLevel()).isEqualTo(8);
            assertThat(skill.getCharacter()).isSameAs(character);
        }

        @Test
        @DisplayName("пустое название, повтор и обычный навык — ошибка")
        void rejected() {
            addSkill(10L, "language_slavic", "Русский", 8);

            assertThatThrownBy(() -> skillService.addCustom(1L, "language_slavic", "  ", 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> skillService.addCustom(1L, "language_slavic", "русский", 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> skillService.addCustom(1L, "handgun", "Глок", 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(character.getSkills()).hasSize(1);
        }

        @Test
        @DisplayName("удаляется только своя строка")
        void deletes() {
            addSkill(10L, "language_slavic", "Русский", 8);
            addSkill(11L, "language_japanese", "Японский", 2);

            skillService.deleteCustom(1L, 10L);

            assertThat(character.getSkills()).extracting(CharacterSkill::getId).containsExactly(11L);
            assertThatThrownBy(() -> skillService.deleteCustom(1L, 10L)).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Роль")
    class RoleTests {

        @Test
        @DisplayName("задаются целиком: несколько ролей, пусто — снять все")
        void setsAndClears() {
            addSkill(10L, "combat_sense", null, 4);
            skillService.setRoles(1L, List.of("solo", "netrunner"));
            assertThat(character.getRoles()).containsExactlyInAnyOrder("solo", "netrunner");

            skillService.setRoles(1L, List.of("cop"));
            assertThat(character.getRoles()).containsExactly("cop");

            skillService.setRoles(1L, null);
            assertThat(character.getRoles()).isEmpty();
        }

        @Test
        @DisplayName("новая роль закрыта, пока спецспособность текущей ниже 4 уровня")
        void newRoleLocked() {
            character.getRoles().add("solo");
            addSkill(10L, "combat_sense", null, 3);

            assertThatThrownBy(() -> skillService.setRoles(1L, List.of("solo", "netrunner")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Новая роль откроется, когда «Боевое чутьё» достигнет уровня 4");
            assertThat(character.getRoles()).containsExactly("solo");
        }

        @Test
        @DisplayName("на 4 уровне открывается одна новая роль, следующая — когда и она дойдёт до 4")
        void rolesOpenOneByOne() {
            character.getRoles().add("solo");
            addSkill(10L, "combat_sense", null, 4);

            skillService.setRoles(1L, List.of("solo", "netrunner"));
            assertThatThrownBy(() -> skillService.setRoles(1L, List.of("solo", "netrunner", "cop")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("«Интерфейс»");

            addSkill(11L, "interface", null, 4);
            skillService.setRoles(1L, List.of("solo", "netrunner", "cop"));
            assertThat(character.getRoles()).containsExactlyInAnyOrder("solo", "netrunner", "cop");
        }

        @Test
        @DisplayName("две новые роли разом нельзя, первую роль можно взять сразу")
        void onlyOneAtATime() {
            assertThatThrownBy(() -> skillService.setRoles(1L, List.of("solo", "netrunner")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("Новые роли берутся по одной");
            assertThat(character.getRoles()).isEmpty();

            skillService.setRoles(1L, List.of("solo"));
            assertThat(character.getRoles()).containsExactly("solo");
        }

        @Test
        @DisplayName("снять роль и заменить её можно всегда, вернуть прокачанную — тоже")
        void removeAndReturn() {
            character.getRoles().add("solo");

            skillService.setRoles(1L, List.of("netrunner"));
            assertThat(character.getRoles()).containsExactly("netrunner");

            addSkill(10L, "combat_sense", null, 5);
            skillService.setRoles(1L, List.of("solo", "netrunner"));
            assertThat(character.getRoles()).containsExactlyInAnyOrder("solo", "netrunner");

            skillService.setRoles(1L, List.of("solo"));
            assertThat(character.getRoles()).containsExactly("solo");
        }

        @Test
        @DisplayName("лист: закрытые роли и подсказка; при 4 уровне всё открыто")
        void sheetOptions() {
            character.getRoles().add("solo");
            CharacterSkill sense = addSkill(10L, "combat_sense", null, 3);
            addSkill(11L, "interface", null, 4);

            SkillSheet sheet = skillService.getSheet(1L);

            assertThat(sheet.roleHint()).isEqualTo("Новая роль откроется, когда «Боевое чутьё» достигнет уровня 4");
            assertThat(option(sheet, "solo").held()).isTrue();
            assertThat(option(sheet, "solo").available()).isTrue();
            assertThat(option(sheet, "cop").available()).isFalse();
            assertThat(option(sheet, "netrunner").available()).isTrue();

            sense.setLevel(4);
            SkillSheet unlocked = skillService.getSheet(1L);
            assertThat(unlocked.roleHint()).isNull();
            assertThat(option(unlocked, "cop").available()).isTrue();
        }

        private SkillSheet.RoleOption option(SkillSheet sheet, String code) {
            return sheet.roleOptions().stream().filter(o -> o.code().equals(code)).findFirst().orElseThrow();
        }

        @Test
        @DisplayName("неизвестная роль — ошибка, прежние остаются")
        void unknownRole() {
            character.getRoles().add("cop");

            assertThatThrownBy(() -> skillService.setRoles(1L, List.of("solo", "paladin")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(character.getRoles()).containsExactly("cop");
        }

        @Test
        @DisplayName("неизвестный персонаж — 404")
        void unknownCharacter() {
            when(characterRepository.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> skillService.setRoles(9L, List.of("solo")))
                    .isInstanceOf(CharacterNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Прокачка")
    class ImproveTests {

        private Counter ip;

        @BeforeEach
        void withIp() {
            ip = new Counter();
            ip.setId(3L);
            ip.setType(Cp2020IpStrategy.TYPE);
            ip.setValue(100);
            character.getCounters().add(ip);
        }

        @Test
        @DisplayName("с нуля: создаёт навык уровня 1 и списывает 10 IP при любом множителе")
        void fromZero() {
            SkillImprovement improvement = skillService.improve(1L, "pilot_gyro", null);

            assertThat(improvement).isEqualTo(new SkillImprovement("Пилот: гирокоптер", 1, 10));
            assertThat(ip.getValue()).isEqualTo(90);
            assertThat(character.getSkills().getFirst().getLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("дальше: цена = уровень × 10 × множитель")
        void nextLevel() {
            CharacterSkill skill = addSkill(10L, "handgun", null, 4);

            SkillImprovement improvement = skillService.improve(1L, "handgun", null);

            assertThat(improvement.cost()).isEqualTo(40);
            assertThat(skill.getLevel()).isEqualTo(5);
            assertThat(ip.getValue()).isEqualTo(60);
        }

        @Test
        @DisplayName("навык со своим названием — по id строки")
        void custom() {
            CharacterSkill skill = addSkill(10L, "martial_art", "Карате", 3);

            SkillImprovement improvement = skillService.improve(1L, null, 10L);

            assertThat(improvement.name()).isEqualTo("Своё боевое искусство: Карате");
            assertThat(improvement.cost()).isEqualTo(60);
            assertThat(skill.getLevel()).isEqualTo(4);
        }

        @Test
        @DisplayName("спецспособность прокачивается у любой своей роли, у чужой — нет")
        void specials() {
            character.getRoles().addAll(List.of("solo", "netrunner"));

            assertThat(skillService.improve(1L, "interface", null).level()).isEqualTo(1);
            assertThat(skillService.improve(1L, "combat_sense", null).level()).isEqualTo(1);
            assertThatThrownBy(() -> skillService.improve(1L, "authority", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(ip.getValue()).isEqualTo(80);
        }

        @Test
        @DisplayName("очков ровно на уровень — хватает; меньше — ошибка, ничего не меняется")
        void balance() {
            CharacterSkill skill = addSkill(10L, "handgun", null, 4);
            ip.setValue(40);
            skillService.improve(1L, "handgun", null);
            assertThat(ip.getValue()).isZero();

            assertThatThrownBy(() -> skillService.improve(1L, "handgun", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("нужно 50 IP, есть 0");
            assertThat(skill.getLevel()).isEqualTo(5);
        }

        @Test
        @DisplayName("уровень 10 — выше нельзя, очки целы")
        void maxLevel() {
            addSkill(10L, "handgun", null, 10);

            assertThatThrownBy(() -> skillService.improve(1L, "handgun", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(ip.getValue()).isEqualTo(100);
        }

        @Test
        @DisplayName("нет счётчика очков или системы с прокачкой — ошибка")
        void unavailable() {
            character.getCounters().clear();
            assertThatThrownBy(() -> skillService.improve(1L, "handgun", null))
                    .isInstanceOf(IllegalArgumentException.class);

            Character generic = characterIn(GenericSystem.CODE);
            when(characterRepository.findById(2L)).thenReturn(Optional.of(generic));
            assertThatThrownBy(() -> skillService.improve(2L, "handgun", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("лист: очки, цена следующего уровня и доступность")
        void sheet() {
            ip.setValue(20);
            addSkill(10L, "handgun", null, 4);
            addSkill(11L, "rifle", null, 10);

            SkillSheet sheet = skillService.getSheet(1L);

            assertThat(sheet.points()).isEqualTo(20);
            assertThat(sheet.pointsName()).isEqualTo("IP");
            assertThat(sheet.isPointsMissing()).isFalse();
            SkillSheet.Row handgun = row(sheet, "REF", "Пистолеты");
            assertThat(handgun.nextCost()).isEqualTo(40);
            assertThat(handgun.affordable()).isFalse();
            assertThat(row(sheet, "REF", "Драка").nextCost()).isEqualTo(10);
            assertThat(row(sheet, "REF", "Драка").affordable()).isTrue();
            assertThat(row(sheet, "REF", "Винтовки").nextCost()).isNull();
        }

        @Test
        @DisplayName("требование не выполнено: на сервере отказ, очки целы; на листе причина вместо покупки")
        void blockedByRequirement() {
            assertThatThrownBy(() -> skillService.improve(1L, "physics", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Математика");
            assertThat(ip.getValue()).isEqualTo(100);

            SkillSheet.Row physics = row(skillService.getSheet(1L), "INT", "Физика");
            assertThat(physics.affordable()).isFalse();
            assertThat(physics.blockedReason()).contains("Математика");
        }

        @Test
        @DisplayName("бонус входит в итог листа и в расчёт проверки")
        void bonusInSheetAndCheck() {
            character.getRoles().add("solo");
            addSkill(10L, "combat_sense", null, 3);
            addSkill(11L, "awareness", null, 2);

            SkillSheet.Row awareness = row(skillService.getSheet(1L), "INT", "Внимательность");
            skillService.check(1L, "awareness", null, 0, null);

            assertThat(awareness.bonus()).isEqualTo(3);
            assertThat(awareness.total()).isEqualTo(5);
            ArgumentCaptor<CheckRequest> request = ArgumentCaptor.forClass(CheckRequest.class);
            verify(checkService).checkForCharacter(eq(1L), request.capture());
            assertThat(request.getValue().modifier()).isEqualTo(5);
            assertThat(request.getValue().parts()).contains(new CheckRequest.Part("Боевое чутьё", 3));
        }

        @Test
        @DisplayName("лист без счётчика очков: прокачка помечена как недоступная")
        void sheetWithoutCounter() {
            character.getCounters().clear();

            SkillSheet sheet = skillService.getSheet(1L);

            assertThat(sheet.isPointsMissing()).isTrue();
            assertThat(row(sheet, "REF", "Драка").affordable()).isFalse();
        }
    }

    @Nested
    @DisplayName("Противопоставленная проверка")
    class OpposedTests {

        private final ArgumentCaptor<CheckRequest> own = ArgumentCaptor.forClass(CheckRequest.class);
        private final ArgumentCaptor<CheckRequest> other = ArgumentCaptor.forClass(CheckRequest.class);

        @Test
        @DisplayName("противник с листом: его стат и навык, а не инициатора")
        void characterOpponent() {
            withStats(8, 0);
            addSkill(10L, "handgun", null, 4);
            Character opponent = characterIn(Cp2020System.CODE);
            opponent.setId(2L);
            CharacterSkill dodge = new CharacterSkill();
            dodge.setSkillCode("handgun");
            dodge.setLevel(2);
            dodge.setCharacter(opponent);
            opponent.getSkills().add(dodge);
            when(characterRepository.findById(2L)).thenReturn(Optional.of(opponent));

            skillService.checkOpposed(1L, "handgun", null, 1, new SkillOpponent(2L, "handgun", null, null, -1));

            verify(checkService).checkOpposed(same(character), own.capture(), same(opponent), isNull(), other.capture());
            assertThat(own.getValue().modifier()).isEqualTo(13); // REF 8 + навык 4 + мод. 1
            assertThat(own.getValue().difficulty()).isNull();
            assertThat(other.getValue().modifier()).isEqualTo(1); // статов нет: навык 2 + мод. −1
        }

        @Test
        @DisplayName("противник без листа: только имя и число; пустое имя — «Противник»")
        void freeOpponent() {
            addSkill(10L, "handgun", null, 4);

            skillService.checkOpposed(1L, "handgun", null, 0, new SkillOpponent(null, null, null, "  ", 12));

            verify(checkService).checkOpposed(same(character), any(), isNull(), eq("Противник"), other.capture());
            assertThat(other.getValue().modifier()).isEqualTo(12);
        }

        @Test
        @DisplayName("противник с листом без навыка — понятная ошибка, броска нет")
        void opponentSkillRequired() {
            assertThatThrownBy(() -> skillService.checkOpposed(1L, "handgun", null, 0,
                    new SkillOpponent(2L, "", null, null, 0)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("навык противника");
            verifyNoInteractions(checkService);
        }

        @Test
        @DisplayName("неизвестный противник — 404-исключение")
        void unknownOpponent() {
            when(characterRepository.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> skillService.checkOpposed(1L, "handgun", null, 0,
                    new SkillOpponent(9L, "handgun", null, null, 0)))
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

            assertThat(check.label()).isEqualTo("Своё боевое искусство: Карате");
            assertThat(captured().skillCode()).isEqualTo("martial_art");
        }

        @Test
        @DisplayName("неизвестный навык, тип без названия и чужая спецспособность — ошибка, броска нет")
        void rejected() {
            assertThatThrownBy(() -> skillService.check(1L, "interface", null, 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> skillService.check(1L, "nope", null, 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> skillService.check(1L, "language_slavic", null, 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(checkService, never()).checkForCharacter(any(), any());
        }
    }
}
