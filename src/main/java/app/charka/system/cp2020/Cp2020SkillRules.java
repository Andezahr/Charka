package app.charka.system.cp2020;

import app.charka.check.CheckRequest;
import app.charka.model.Character;
import app.charka.model.CharacterSkill;
import app.charka.skill.SkillDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Правила книги о связях навыков между собой: бонусы к проверке, языки одной семьи, требования для прокачки.
 */
final class Cp2020SkillRules {

    private static final String LANGUAGE_PREFIX = "language_";
    private static final String SOLO_ROLE = "solo";
    /** Oratory от этого уровня даёт +1 к Charismatic Leadership (книга, с. 47) */
    private static final int ORATORY_BONUS_LEVEL = 6;

    private record Requirement(String skillCode, int level) {
    }

    /** Какой навык и какого уровня нужен для прокачки (книга, с. 49, 53) */
    private static final Map<String, Requirement> REQUIREMENTS = Map.of(
            "physics", new Requirement("mathematics", 4),
            "pharmaceuticals", new Requirement("chemistry", 4));

    private Cp2020SkillRules() {
    }

    /**
     * Прибавки к проверке сверх стата и уровня (книга, с. 46, 47, 49): Боевое чутьё Соло к Внимательности,
     * Ораторство к Харизматичному лидерству, половина уровня родственного языка.
     */
    static List<CheckRequest.Part> bonuses(Character character, SkillDefinition definition, CharacterSkill skill) {
        List<CheckRequest.Part> parts = new ArrayList<>();
        if ("awareness".equals(definition.code())) {
            combatSense(character).ifPresent(parts::add);
        }
        if ("charismatic_leadership".equals(definition.code()) && level(character, "oratory") >= ORATORY_BONUS_LEVEL) {
            parts.add(new CheckRequest.Part(name("oratory"), 1));
        }
        if (isLanguage(definition)) {
            int own = skill == null ? 0 : skill.getLevel();
            sameFamily(character, definition, skill).stream()
                    .max(Comparator.comparingInt(CharacterSkill::getLevel))
                    .filter(best -> best.getLevel() / 2 > own)
                    .ifPresent(best -> parts.add(new CheckRequest.Part(
                            "Родственный язык: " + best.getCustomName(), best.getLevel() / 2 - own)));
        }
        return parts;
    }

    /**
     * Боевое чутьё даёт прибавку только Соло: у сменившего роль уровень остаётся, но не работает (книга, с. 46, 97).
     */
    static Optional<CheckRequest.Part> combatSense(Character character) {
        int level = character.getRoles().contains(SOLO_ROLE) ? level(character, "combat_sense") : 0;
        return level > 0 ? Optional.of(new CheckRequest.Part(name("combat_sense"), level)) : Optional.empty();
    }

    /**
     * Множитель цены с учётом языков (книга, с. 49): после первого языка семьи остальные на 1 дешевле, но не меньше ×1.
     * Первым считается добавленный раньше, иначе два языка семьи удешевляли бы друг друга.
     */
    static int costMultiplier(Character character, SkillDefinition definition, CharacterSkill skill) {
        boolean discounted = isLanguage(definition) && skill != null && skill.getId() != null
                && sameFamily(character, definition, skill).stream()
                        .anyMatch(other -> other.getLevel() > 0 && other.getId() != null && other.getId() < skill.getId());
        return discounted ? Math.max(definition.costMultiplier() - 1, 1) : definition.costMultiplier();
    }

    /**
     * Почему навык нельзя повышать; пусто — можно.
     */
    static Optional<String> blockedReason(Character character, SkillDefinition definition) {
        Requirement requirement = REQUIREMENTS.get(definition.code());
        if (requirement == null || level(character, requirement.skillCode()) >= requirement.level()) {
            return Optional.empty();
        }
        return Optional.of("Нужен навык «" + name(requirement.skillCode()) + "» не ниже " + requirement.level());
    }

    private static boolean isLanguage(SkillDefinition definition) {
        return definition.code().startsWith(LANGUAGE_PREFIX);
    }

    private static List<CharacterSkill> sameFamily(Character character, SkillDefinition definition, CharacterSkill skill) {
        return character.getSkills().stream()
                .filter(other -> other != skill && other.getSkillCode().equals(definition.code()))
                .toList();
    }

    private static int level(Character character, String code) {
        return character.getSkills().stream()
                .filter(skill -> skill.getCustomName() == null && skill.getSkillCode().equals(code))
                .mapToInt(CharacterSkill::getLevel)
                .findFirst()
                .orElse(0);
    }

    private static String name(String code) {
        return Cp2020Skills.CATALOG.stream()
                .filter(skill -> skill.code().equals(code))
                .map(SkillDefinition::name)
                .findFirst()
                .orElse(code);
    }
}
