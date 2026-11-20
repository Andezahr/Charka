package app.charka.system.kosmozeki;

import app.charka.model.Character;
import app.charka.system.ProfileField;
import java.util.List;
import app.charka.system.SheetRules;

/**
 * ARC-01: лист персонажа: поля профиля, строка здоровья, заголовок контактов, лист на бумаге — правила этой системы (вынесено из KzSystem).
 */
class KzSheetRules implements SheetRules {

    /** PH-21: оформление кампании — «зона»: ржавчина, хаки, бетон */
    @Override
    public String theme() {
        return "kosmozeki";
    }


    @Override
    public List<ProfileField> getProfileFields() {
        return KzProfile.FIELDS;
    }

    @Override
    public String getHealthStatus(Character character) {
        return KzAbilities.bodyCounter(character).map(KzSystem::healthBrief).orElse(null);
    }
}
