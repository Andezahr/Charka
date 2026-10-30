package app.charka.skill;

import app.charka.check.CheckRequest;
import app.charka.check.CheckService;
import app.charka.check.OpposedCheckResult;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Навыки и роли персонажа. Какие навыки и роли существуют, как считаются статы и сколько стоит прокачка — решает
 * система кампании.
 */
@Service
@RequiredArgsConstructor
public class SkillService {

    static final int MAX_LEVEL = 10;
    private static final int MAX_CUSTOM_NAME = 100;
    private static final String SPECIAL_GROUP = "Спецспособности ролей";
    private static final String DEFAULT_OPPONENT_NAME = "Противник";

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
        List<CharacterRole> heldRoles = system.rolesOf(character);
        SkillProgression progression = system.getSkillProgression();
        Integer points = progression == null ? null : progression.balance(character);

        List<SkillSheet.Row> specials = new ArrayList<>();
        Map<String, List<SkillSheet.Row>> byStat = new LinkedHashMap<>();
        Map<String, List<SkillDefinition>> customTypes = new LinkedHashMap<>();
        for (SkillDefinition definition : system.getSkillCatalog()) {
            List<SkillSheet.Row> rows = definition.special()
                    ? specials
                    : byStat.computeIfAbsent(definition.statCode(), key -> new ArrayList<>());
            if (definition.custom()) {
                customTypes.computeIfAbsent(definition.statCode(), key -> new ArrayList<>()).add(definition);
                customSkills(character, definition.code()).forEach(skill ->
                        rows.add(row(character, system, definition, skill, stats, null, points)));
            } else if (!definition.special()) {
                rows.add(row(character, system, definition, plainSkill(character, definition.code()), stats, null, points));
            } else {
                heldRoles.stream()
                        .filter(role -> definition.code().equals(role.specialSkillCode()))
                        .findFirst()
                        .ifPresent(role -> rows.add(row(character, system, definition, plainSkill(character, definition.code()),
                                stats, role.name(), points)));
            }
        }

        List<SkillSheet.Group> groups = new ArrayList<>();
        if (!specials.isEmpty()) {
            groups.add(new SkillSheet.Group(SPECIAL_GROUP, null, true, List.of(), specials));
        }
        byStat.forEach((statCode, rows) -> {
            if (!rows.isEmpty()) {
                StatValue stat = stats.get(statCode);
                groups.add(new SkillSheet.Group(statName(statCode, stat), stat, false,
                        customTypes.getOrDefault(statCode, List.of()), rows));
            }
        });
        List<CharacterRole> unfinished = rolesBelowUnlock(character, system, heldRoles);
        List<SkillSheet.RoleOption> roleOptions = system.getRoles().stream()
                .map(role -> {
                    boolean held = heldRoles.contains(role);
                    boolean available = held || unfinished.isEmpty()
                            || rolesBelowUnlock(character, system, List.of(role)).isEmpty();
                    return new SkillSheet.RoleOption(role.code(), role.name(), held, available);
                })
                .toList();
        String roleHint = unfinished.isEmpty() ? null : unlockHint(system, unfinished);
        return new SkillSheet(heldRoles, roleOptions, roleHint, groups, stats.isEmpty(),
                progression == null ? null : progression.getPointsName(), points);
    }

    /**
     * Меняет уровни разом, без траты очков: создание персонажа, правка мастера.
     * Уровень 0 у обычного навыка удаляет его строку.
     *
     * @param levels       код навыка → уровень
     * @param customLevels id строки навыка со своим названием → уровень
     */
    @Transactional
    public void updateLevels(Long characterId, Map<String, Integer> levels, Map<Long, Integer> customLevels) {
        Character character = getCharacter(characterId);
        GameSystem system = systemRegistry.forCharacter(character);
        levels.forEach((code, level) -> {
            SkillDefinition definition = plainDefinition(character, system, code);
            setPlainLevel(character, definition.code(), checkLevel(level));
        });
        customLevels.forEach((skillId, level) -> customSkill(character, skillId).setLevel(checkLevel(level)));
    }

    /**
     * Повышает навык на один уровень за очки по правилам системы.
     *
     * @param code    код обычного навыка; не используется, если задан {@code skillId}
     * @param skillId id строки навыка со своим названием
     */
    @Transactional
    public SkillImprovement improve(Long characterId, String code, Long skillId) {
        Character character = getCharacter(characterId);
        GameSystem system = systemRegistry.forCharacter(character);
        SkillProgression progression = system.getSkillProgression();
        if (progression == null) {
            throw new IllegalArgumentException("В этой системе навыки не развиваются за очки");
        }
        CharacterSkill custom = skillId != null ? customSkill(character, skillId) : null;
        SkillDefinition definition = custom != null
                ? definition(system, custom.getSkillCode())
                : plainDefinition(character, system, code);
        CharacterSkill skill = custom != null ? custom : plainSkill(character, code);
        int level = skill == null ? 0 : skill.getLevel();
        if (level >= MAX_LEVEL) {
            throw new IllegalArgumentException("Навык «" + displayName(definition, skill) + "» уже на максимальном уровне");
        }

        String blockedReason = progression.blockedReason(character, definition);
        if (blockedReason != null) {
            throw new IllegalArgumentException(blockedReason);
        }

        int cost = progression.cost(character, definition, skill, level);
        Integer balance = progression.balance(character);
        if (balance == null) {
            throw new IllegalArgumentException("У персонажа нет счётчика очков " + progression.getPointsName());
        }
        if (balance < cost) {
            throw new IllegalArgumentException("Не хватает очков: нужно " + cost + " " + progression.getPointsName()
                    + ", есть " + balance);
        }
        progression.spend(character, cost);
        if (custom != null) {
            custom.setLevel(level + 1);
        } else {
            setPlainLevel(character, definition.code(), level + 1);
        }
        return new SkillImprovement(displayName(definition, skill), level + 1, cost);
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
     * Задаёт роли персонажа целиком. Уровень спецспособности снятой роли сохраняется:
     * способность недоступна, пока роль не вернут. Если система ограничивает новые роли
     * ({@link GameSystem#getRoleUnlockLevel()}), при добавлении после изменения может остаться
     * не больше одной роли ниже порога: новые роли берутся по одной.
     *
     * @param roleCodes коды ролей системы; пусто — снять все
     */
    @Transactional
    public void setRoles(Long characterId, Collection<String> roleCodes) {
        Character character = getCharacter(characterId);
        GameSystem system = systemRegistry.forCharacter(character);
        Set<String> codes = new HashSet<>();
        List<CharacterRole> requested = new ArrayList<>();
        if (roleCodes != null) {
            for (String code : roleCodes) {
                CharacterRole role = system.findRole(code)
                        .orElseThrow(() -> new IllegalArgumentException("Неизвестная роль: " + code));
                if (codes.add(role.code())) {
                    requested.add(role);
                }
            }
        }
        boolean adding = !character.getRoles().containsAll(codes);
        if (adding && rolesBelowUnlock(character, system, requested).size() > 1) {
            List<CharacterRole> unfinished = rolesBelowUnlock(character, system, system.rolesOf(character));
            throw new IllegalArgumentException(unfinished.isEmpty()
                    ? "Новые роли берутся по одной: следующая откроется, когда спецспособность предыдущей достигнет уровня "
                            + system.getRoleUnlockLevel()
                    : unlockHint(system, unfinished));
        }
        character.getRoles().clear();
        character.getRoles().addAll(codes);
    }

    /**
     * Проверка навыка: текущий стат + уровень + {@code modifier} по правилам системы, пишется в лог бросков.
     *
     * @param code    код обычного навыка; не используется, если задан {@code skillId}
     * @param skillId id строки навыка со своим названием
     */
    @Transactional
    public SkillCheck check(Long characterId, String code, Long skillId, int modifier, Integer difficulty) {
        CheckRequest request = skillRequest(getCharacter(characterId), code, skillId, modifier, difficulty);
        return new SkillCheck(request.label(), checkService.checkForCharacter(characterId, request));
    }

    /**
     * Противопоставленная проверка навыка: обе стороны бросают, сложности нет, пишется в лог бросков.
     *
     * @param code    код обычного навыка инициатора; не используется, если задан {@code skillId}
     * @param skillId id строки навыка со своим названием
     */
    @Transactional
    public OpposedCheckResult checkOpposed(Long characterId, String code, Long skillId, int modifier, SkillOpponent opponent) {
        Character character = getCharacter(characterId);
        CheckRequest request = skillRequest(character, code, skillId, modifier, null);
        if (opponent.characterId() == null) {
            String name = opponent.name() == null || opponent.name().isBlank()
                    ? DEFAULT_OPPONENT_NAME
                    : opponent.name().strip();
            return checkService.checkOpposed(character, request, null, name,
                    new CheckRequest(name, opponent.modifier(), null));
        }
        if (opponent.skillId() == null && (opponent.code() == null || opponent.code().isBlank())) {
            throw new IllegalArgumentException("Выберите навык противника");
        }
        Character other = getCharacter(opponent.characterId());
        CheckRequest otherRequest = skillRequest(other, opponent.code(), opponent.skillId(), opponent.modifier(), null);
        return checkService.checkOpposed(character, request, other, null, otherRequest);
    }

    private CheckRequest skillRequest(Character character, String code, Long skillId, int modifier, Integer difficulty) {
        GameSystem system = systemRegistry.forCharacter(character);
        CharacterSkill skill = skillId != null ? customSkill(character, skillId) : plainSkill(character, code);
        SkillDefinition definition = skillId != null
                ? definition(system, skill.getSkillCode())
                : plainDefinition(character, system, code);

        StatValue stat = system.getCurrentStats(character).get(definition.statCode());
        String name = displayName(definition, skill);
        List<CheckRequest.Part> parts = new ArrayList<>();
        parts.add(new CheckRequest.Part(statName(definition.statCode(), stat), stat == null ? 0 : stat.value()));
        parts.add(new CheckRequest.Part(name, skill == null ? 0 : skill.getLevel()));
        parts.addAll(system.getSkillBonuses(character, definition, skill));
        if (modifier != 0) {
            parts.add(new CheckRequest.Part("Мод.", modifier));
        }
        return CheckRequest.ofParts(name, parts, difficulty, definition.code());
    }

    /**
     * Роли, спецспособность которых ещё ниже порога для новой роли; без порога в системе — ни одной.
     */
    private static List<CharacterRole> rolesBelowUnlock(Character character, GameSystem system, List<CharacterRole> roles) {
        int unlockLevel = system.getRoleUnlockLevel();
        return roles.stream()
                .filter(role -> role.specialSkillCode() != null)
                .filter(role -> {
                    CharacterSkill skill = plainSkill(character, role.specialSkillCode());
                    return (skill == null ? 0 : skill.getLevel()) < unlockLevel;
                })
                .toList();
    }

    private static String unlockHint(GameSystem system, List<CharacterRole> unfinished) {
        String abilities = unfinished.stream()
                .map(role -> "«" + definition(system, role.specialSkillCode()).name() + "»")
                .collect(Collectors.joining(", "));
        return "Новая роль откроется, когда " + abilities + " достигнет уровня " + system.getRoleUnlockLevel();
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

    private static SkillSheet.Row row(Character character, GameSystem system, SkillDefinition definition, CharacterSkill skill,
                                      Map<String, StatValue> stats, String roleName, Integer points) {
        SkillProgression progression = system.getSkillProgression();
        StatValue stat = stats.get(definition.statCode());
        int statValue = stat == null ? 0 : stat.value();
        int level = skill == null ? 0 : skill.getLevel();
        int bonus = system.getSkillBonuses(character, definition, skill).stream().mapToInt(CheckRequest.Part::value).sum();
        Integer nextCost = progression == null || level >= MAX_LEVEL
                ? null
                : progression.cost(character, definition, skill, level);
        String blockedReason = progression == null ? null : progression.blockedReason(character, definition);
        boolean affordable = nextCost != null && blockedReason == null && points != null && points >= nextCost;
        return new SkillSheet.Row(definition.code(), definition.custom() ? skill.getId() : null,
                displayName(definition, skill), level, statValue + level + bonus,
                statName(definition.statCode(), stat), statValue, roleName, nextCost, affordable, bonus, blockedReason);
    }

    private static String displayName(SkillDefinition definition, CharacterSkill skill) {
        return definition.custom() ? definition.name() + ": " + skill.getCustomName() : definition.name();
    }

    private static String statName(String statCode, StatValue stat) {
        return stat == null ? statCode.toUpperCase(Locale.ROOT) : stat.name();
    }

    private static int checkLevel(Integer level) {
        if (level == null || level < 0 || level > MAX_LEVEL) {
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

    /**
     * Навык, к которому обращаются по коду: не со своим названием, а спецспособность — только своей роли.
     */
    private static SkillDefinition plainDefinition(Character character, GameSystem system, String code) {
        SkillDefinition definition = definition(system, code);
        if (definition.custom()) {
            throw new IllegalArgumentException("Выберите конкретный навык «" + definition.name() + "»");
        }
        boolean foreignSpecial = definition.special() && system.rolesOf(character).stream()
                .noneMatch(role -> code.equals(role.specialSkillCode()));
        if (foreignSpecial) {
            throw new IllegalArgumentException("Спецспособность «" + definition.name() + "» доступна только своей роли");
        }
        return definition;
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
