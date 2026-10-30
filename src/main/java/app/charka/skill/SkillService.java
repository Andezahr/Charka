package app.charka.skill;

import app.charka.check.CheckRequest;
import app.charka.check.CheckService;
import app.charka.exception.CharacterNotFoundException;
import app.charka.exception.NotFoundException;
import app.charka.model.Character;
import app.charka.model.CharacterSkill;
import app.charka.repository.CharacterRepository;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Навыки и роль персонажа. Какие навыки и роли существуют и как считаются статы — решает система кампании.
 */
@Service
@RequiredArgsConstructor
public class SkillService {

    static final int MAX_LEVEL = 10;
    private static final int MAX_CUSTOM_NAME = 100;
    private static final String SPECIAL_GROUP = "Спецспособность";

    private final CharacterRepository characterRepository;
    private final GameSystemRegistry systemRegistry;
    private final CheckService checkService;

    /**
     * Есть ли у системы персонажа навыки — от этого зависит вкладка «Навыки».
     */
    @Transactional(readOnly = true)
    public boolean hasCatalog(Long characterId) {
        return characterRepository.findById(characterId)
                .map(character -> !systemRegistry.forCharacter(character).getSkillCatalog().isEmpty())
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public SkillSheet getSheet(Long characterId) {
        Character character = getCharacter(characterId);
        GameSystem system = systemRegistry.forCharacter(character);
        Map<String, StatValue> stats = system.getCurrentStats(character);
        CharacterRole role = system.findRole(character.getRole()).orElse(null);

        List<SkillSheet.Row> specials = new ArrayList<>();
        Map<String, List<SkillSheet.Row>> byStat = new LinkedHashMap<>();
        List<SkillDefinition> customTypes = new ArrayList<>();
        for (SkillDefinition definition : system.getSkillCatalog()) {
            List<SkillSheet.Row> rows = definition.special()
                    ? specials
                    : byStat.computeIfAbsent(definition.statCode(), key -> new ArrayList<>());
            if (definition.custom()) {
                customTypes.add(definition);
                customSkills(character, definition.code()).forEach(skill ->
                        rows.add(row(definition, skill, stats)));
            } else {
                CharacterSkill skill = plainSkill(character, definition.code());
                boolean ownSpecial = role != null && definition.code().equals(role.specialSkillCode());
                if (!definition.special() || ownSpecial || skill != null) {
                    rows.add(row(definition, skill, stats));
                }
            }
        }

        List<SkillSheet.Group> groups = new ArrayList<>();
        if (!specials.isEmpty()) {
            groups.add(new SkillSheet.Group(SPECIAL_GROUP, null, true, specials));
        }
        byStat.forEach((statCode, rows) -> {
            if (!rows.isEmpty()) {
                StatValue stat = stats.get(statCode);
                groups.add(new SkillSheet.Group(statName(statCode, stat), stat, false, rows));
            }
        });
        return new SkillSheet(role, system.getRoles(), groups, customTypes, stats.isEmpty());
    }

    /**
     * Меняет уровни разом (форма страницы навыков). Уровень 0 у обычного навыка удаляет его строку.
     *
     * @param levels       код навыка → уровень
     * @param customLevels id строки навыка со своим названием → уровень
     */
    @Transactional
    public void updateLevels(Long characterId, Map<String, Integer> levels, Map<Long, Integer> customLevels) {
        Character character = getCharacter(characterId);
        GameSystem system = systemRegistry.forCharacter(character);
        levels.forEach((code, level) -> {
            SkillDefinition definition = definition(system, code);
            if (definition.custom()) {
                throw new IllegalArgumentException("У навыка «" + definition.name() + "» уровень задаётся отдельно для каждого названия");
            }
            setPlainLevel(character, code, checkLevel(level));
        });
        customLevels.forEach((skillId, level) -> customSkill(character, skillId).setLevel(checkLevel(level)));
    }

    /**
     * Добавляет навык со своим названием: язык, «Эксперт: …», боевое искусство.
     */
    @Transactional
    public void addCustom(Long characterId, String code, String customName, int level) {
        Character character = getCharacter(characterId);
        SkillDefinition definition = definition(systemRegistry.forCharacter(character), code);
        if (!definition.custom()) {
            throw new IllegalArgumentException("Навыку «" + definition.name() + "» нельзя задать своё название");
        }
        String name = customName == null ? "" : customName.strip();
        if (name.isEmpty() || name.length() > MAX_CUSTOM_NAME) {
            throw new IllegalArgumentException("Название навыка — от 1 до " + MAX_CUSTOM_NAME + " символов");
        }
        boolean duplicate = customSkills(character, code).stream()
                .anyMatch(skill -> skill.getCustomName().equalsIgnoreCase(name));
        if (duplicate) {
            throw new IllegalArgumentException("Навык «" + definition.name() + ": " + name + "» уже есть");
        }
        CharacterSkill skill = new CharacterSkill();
        skill.setCharacter(character);
        skill.setSkillCode(code);
        skill.setCustomName(name);
        skill.setLevel(checkLevel(level));
        character.getSkills().add(skill);
    }

    @Transactional
    public void deleteCustom(Long characterId, Long skillId) {
        Character character = getCharacter(characterId);
        CharacterSkill skill = customSkill(character, skillId);
        character.getSkills().removeIf(other -> other == skill);
    }

    /**
     * @param roleCode код роли системы; пусто — снять роль. Навыки при смене роли не теряются.
     */
    @Transactional
    public void setRole(Long characterId, String roleCode) {
        Character character = getCharacter(characterId);
        if (roleCode == null || roleCode.isBlank()) {
            character.setRole(null);
            return;
        }
        CharacterRole role = systemRegistry.forCharacter(character).findRole(roleCode)
                .orElseThrow(() -> new IllegalArgumentException("Неизвестная роль: " + roleCode));
        character.setRole(role.code());
    }

    /**
     * Проверка навыка: текущий стат + уровень + {@code modifier} по правилам системы, пишется в лог бросков.
     *
     * @param code    код обычного навыка; не используется, если задан {@code skillId}
     * @param skillId id строки навыка со своим названием
     */
    @Transactional
    public SkillCheck check(Long characterId, String code, Long skillId, int modifier, Integer difficulty) {
        Character character = getCharacter(characterId);
        GameSystem system = systemRegistry.forCharacter(character);
        CharacterSkill skill = skillId != null ? customSkill(character, skillId) : plainSkill(character, code);
        SkillDefinition definition = definition(system, skillId != null ? skill.getSkillCode() : code);
        if (skillId == null && definition.custom()) {
            throw new IllegalArgumentException("Выберите конкретный навык «" + definition.name() + "»");
        }

        StatValue stat = system.getCurrentStats(character).get(definition.statCode());
        String name = displayName(definition, skill);
        List<CheckRequest.Part> parts = new ArrayList<>();
        parts.add(new CheckRequest.Part(statName(definition.statCode(), stat), stat == null ? 0 : stat.value()));
        parts.add(new CheckRequest.Part(name, skill == null ? 0 : skill.getLevel()));
        if (modifier != 0) {
            parts.add(new CheckRequest.Part("Мод.", modifier));
        }
        CheckRequest request = CheckRequest.ofParts(name, parts, difficulty, definition.code());
        return new SkillCheck(name, checkService.checkForCharacter(characterId, request));
    }

    private void setPlainLevel(Character character, String code, int level) {
        CharacterSkill skill = plainSkill(character, code);
        if (level == 0) {
            if (skill != null) {
                character.getSkills().removeIf(other -> other == skill);
            }
        } else if (skill != null) {
            skill.setLevel(level);
        } else {
            CharacterSkill created = new CharacterSkill();
            created.setCharacter(character);
            created.setSkillCode(code);
            created.setLevel(level);
            character.getSkills().add(created);
        }
    }

    private static SkillSheet.Row row(SkillDefinition definition, CharacterSkill skill, Map<String, StatValue> stats) {
        StatValue stat = stats.get(definition.statCode());
        int statValue = stat == null ? 0 : stat.value();
        int level = skill == null ? 0 : skill.getLevel();
        return new SkillSheet.Row(definition.code(), definition.custom() ? skill.getId() : null,
                displayName(definition, skill), level, statValue + level,
                statName(definition.statCode(), stat), statValue, definition.costMultiplier());
    }

    private static String displayName(SkillDefinition definition, CharacterSkill skill) {
        return definition.custom() ? definition.name() + ": " + skill.getCustomName() : definition.name();
    }

    private static String statName(String statCode, StatValue stat) {
        return stat == null ? statCode.toUpperCase(Locale.ROOT) : stat.name();
    }

    private static int checkLevel(Integer level) {        if (level == null || level < 0 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("Уровень навыка должен быть от 0 до " + MAX_LEVEL);
        }
        return level;
    }

    private static SkillDefinition definition(GameSystem system, String code) {
        return system.getSkillCatalog().stream()
                .filter(definition -> definition.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Неизвестный навык: " + code));
    }

    private static CharacterSkill plainSkill(Character character, String code) {
        return character.getSkills().stream()
                .filter(skill -> skill.getCustomName() == null && skill.getSkillCode().equals(code))
                .findFirst()
                .orElse(null);
    }

    private static List<CharacterSkill> customSkills(Character character, String code) {
        return character.getSkills().stream()
                .filter(skill -> skill.getCustomName() != null && skill.getSkillCode().equals(code))
                .toList();
    }

    private static CharacterSkill customSkill(Character character, Long skillId) {
        return character.getSkills().stream()
                .filter(skill -> skill.getCustomName() != null && skillId.equals(skill.getId()))
                .findFirst()
                .orElseThrow(NotFoundException.forId("Skill", skillId));
    }

    private Character getCharacter(Long characterId) {
        return characterRepository.findById(characterId)
                .orElseThrow(CharacterNotFoundException.forId(characterId));
    }
}
