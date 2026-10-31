package app.charka.encounter;

import app.charka.attack.AttackForm;
import app.charka.item.ItemAction;
import app.charka.item.ItemSummary;
import app.charka.model.Character;
import app.charka.model.CombatEffect;
import app.charka.model.Encounter;
import app.charka.model.EncounterParticipant;
import app.charka.model.Item;
import app.charka.model.PendingBlast;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Страница идущего боя.
 *
 * @param current    чей сейчас ход; null — ходить некому
 * @param lastInRound следующий ход начнёт новый раунд
 * @param rows       участники в порядке хода
 * @param addable    кого ещё можно ввести в бой
 * @param roundFresh  никто ещё не походил: можно объявлять то, что объявляют в начале раунда
 */
public record EncounterView(Encounter encounter, Character current, boolean lastInRound, List<Row> rows, List<Character> addable, boolean roundFresh) {

    /**
     * @param declaration  что объявлено в этом раунде; null — ничего
     * @param declarations что вообще можно объявить
     * @param checks       навыки для проверки из строки
     * @param targets цели атаки: живые участники, сначала другая сторона
     * @param effects что длится на участнике
     * @param abilities чем можно бить без предмета
     */
    public record Row(EncounterParticipant participant, Character character, InitiativeDeclaration declaration,
                      List<InitiativeDeclaration> declarations, List<CheckOption> checks, CombatCard card, boolean current,
                      List<Weapon> weapons, List<Character> targets, List<PendingBlast> blasts, List<CombatEffect> effects, List<Ability> abilities) {

        /** Итог действия с этим предметом показывается в этой строке */
        public boolean hasItem(Long itemId) {
            return weapons.stream().anyMatch(weapon -> weapon.item().getId().equals(itemId));
        }

        /** Модификаторы атаки, которые уже отмечены в диалоге: ослеплённому не надо искать свой */
        public List<String> attackOptions() {
            return Stream.concat(effects.stream().map(CombatEffect::getAttackOption),
                            Stream.of(declaration).filter(Objects::nonNull).map(InitiativeDeclaration::attackOption))
                    .filter(Objects::nonNull).toList();
        }
    }

    /**
     * @param url адрес предмета, от него строятся адреса действий
     */
    public record Weapon(Item item, ItemSummary summary, AttackForm form, List<ItemAction> actions, String url) {
    }

    /**
     * @param value код навыка для проверки
     * @param label название и итог
     */
    public record CheckOption(String value, String label) {
    }

    /**
     * Атака без предмета.
     *
     * @param id   для диалога на странице: у каждого участника свой
     * @param url  куда отправить атаку
     * @param name подпись
     */
    public record Ability(String id, String url, String name, AttackForm form) {
    }

    /** Кого можно взять в бой при его начале */
    public record Candidate(Character character, CombatCard card) {
    }
}
