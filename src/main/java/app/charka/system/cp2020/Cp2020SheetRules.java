package app.charka.system.cp2020;

import app.charka.model.Character;
import app.charka.system.SheetRules;

/**
 * ARC-01: лист персонажа: поля профиля, строка здоровья, заголовок контактов, лист на бумаге — правила этой системы (вынесено из Cp2020System).
 */
class Cp2020SheetRules implements SheetRules {

    /** PH-21: оформление кампании — нынешний неон (токены app.css без изменений) */
    @Override
    public String theme() {
        return "cp2020";
    }


    private final Cp2020System system;

    Cp2020SheetRules(Cp2020System system) {
        this.system = system;
    }

    @Override
    public String getHealthStatus(Character character) {
        return character.getCounters().stream()
                .filter(counter -> Cp2020HealthStrategy.TYPE.equals(counter.getType()))
                .findFirst()
                .map(Cp2020System::healthBrief)
                .orElse(null);
    }

    /** KF-07: в книге — Contacts: фиксеры, копы, бывшие */
    @Override
    public String getContactsTitle() {
        return "Контакты";
    }

    @Override
    public app.charka.system.PrintSheet getPrintSheet(Character character) {
        return Cp2020Print.sheet(character, system.skills().getCurrentStats(character), Cp2020System.ARMOR);
    }
}
