package app.charka.system.kosmozeki;

import app.charka.creation.CharacterCreation;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.service.CharacterService;
import app.charka.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Мастер создания персонажа Космозэков (K-11): одна страница — имя, три тега, приговор, качества, роль, предыстория.
 * Чисел нет: тело «Человек» и вдохновение создаются сами.
 */
@Component
@RequiredArgsConstructor
public class KzCreation implements CharacterCreation {

    private final CharacterService characterService;

    /**
     * Что нужно странице.
     *
     * @param profile поля профиля (пустые, с обязательными качествами)
     * @param roles   функциональные роли
     */
    public record Form(List<ProfileService.Row> profile, List<app.charka.skill.CharacterRole> roles) {
    }

    @Override
    public String getSystemCode() {
        return KzSystem.CODE;
    }

    @Override
    public String getTemplate() {
        return "creation/kz";
    }

    @Override
    public Object form(Campaign campaign) {
        return new Form(ProfileService.rows(new Character(), KzProfile.FIELDS), KzSystem.ROLES);
    }

    @Override
    public Roll roll(Campaign campaign, String method) {
        throw new IllegalArgumentException("В Космозэках очков нет");
    }

    @Override
    public Character create(Campaign campaign, Map<String, String> params) {
        String name = params.getOrDefault("name", "").strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Как зовут персонажа?");
        }
        String qualities = String.join(", ", KzProfile.FIELDS.stream()
                .filter(field -> field.key().equals(KzProfile.QUALITIES))
                .flatMap(field -> field.suggestions().stream())
                .filter(option -> params.containsKey("pick." + KzProfile.QUALITIES + "." + option))
                .toList());
        for (String required : KzProfile.REQUIRED_QUALITIES) {
            if (!qualities.contains(required)) {
                throw new IllegalArgumentException("Качество «" + required + "» обязательно");
            }
        }
        Character character = new Character();
        character.setName(name);
        ProfileService.apply(character, KzProfile.FIELDS, params);
        KzSystem.ROLES.stream()
                .filter(role -> params.containsKey("role." + role.code()))
                .forEach(role -> character.getRoles().add(role.code()));
        return characterService.createInCampaign(campaign.getId(), character);
    }
}
