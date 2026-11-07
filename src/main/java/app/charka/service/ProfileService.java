package app.charka.service;

import app.charka.exception.CharacterNotFoundException;
import app.charka.model.Character;
import app.charka.repository.CharacterRepository;
import app.charka.system.GameSystemRegistry;
import app.charka.system.ProfileField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * K-11 [CORE]: поля листа, которые объявляет система ({@link ProfileField}): теги, приговор, качества.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    /** Колонка profile_value */
    private static final int MAX_VALUE = 4000;

    private final CharacterRepository characterRepository;
    private final GameSystemRegistry systemRegistry;

    /**
     * Записать поля из формы: {@code profile.<ключ>} — текст, {@code pick.<ключ>.<вариант>} — выбранный вариант поля с выбором.
     * Поля, которых в форме нет, не меняются; у поля с выбором — если в форме есть маркер {@code multi.<ключ>}.
     *
     * @throws IllegalArgumentException слишком длинный текст
     */
    @Transactional
    public Character update(Long characterId, Map<String, String> params) {
        Character character = characterRepository.findById(characterId).orElseThrow(CharacterNotFoundException.forId(characterId));
        apply(character, systemRegistry.forCharacter(character).getProfileFields(), params);
        return characterRepository.save(character);
    }

    /** Поля формы → профиль персонажа (и при создании, до сохранения) */
    public static void apply(Character character, List<ProfileField> fields, Map<String, String> params) {
        for (ProfileField field : fields) {
            String value;
            if (field.multi()) {
                if (!params.containsKey("multi." + field.key())) {
                    continue;
                }
                Set<String> picked = new LinkedHashSet<>();
                for (String option : field.suggestions()) {
                    if (params.containsKey("pick." + field.key() + "." + option)) {
                        picked.add(option);
                    }
                }
                value = String.join(", ", picked);
            } else {
                if (!params.containsKey("profile." + field.key())) {
                    continue;
                }
                value = params.get("profile." + field.key()).strip();
            }
            if (value.length() > MAX_VALUE) {
                throw new IllegalArgumentException("«" + field.label() + "» длиннее " + MAX_VALUE + " символов");
            }
            if (value.isEmpty()) {
                character.getProfile().remove(field.key());
            } else {
                character.getProfile().put(field.key(), value);
            }
        }
    }

    /**
     * Строки для листа: подпись и значение; у поля с выбором — выбранное списком.
     *
     * @param chosen выбранные варианты поля с выбором (для галочек); пусто у текстового поля
     */
    public record Row(ProfileField field, String value, Set<String> chosen) {
    }

    public static List<Row> rows(Character character, List<ProfileField> fields) {
        List<Row> rows = new ArrayList<>();
        for (ProfileField field : fields) {
            String value = character.getProfile() == null ? null : character.getProfile().get(field.key());
            Set<String> chosen = new LinkedHashSet<>();
            if (field.multi()) {
                if (value == null) {
                    chosen.addAll(field.required());
                } else {
                    for (String part : value.split(",")) {
                        if (!part.isBlank()) {
                            chosen.add(part.strip());
                        }
                    }
                }
            }
            rows.add(new Row(field, value, chosen));
        }
        return rows;
    }
}
