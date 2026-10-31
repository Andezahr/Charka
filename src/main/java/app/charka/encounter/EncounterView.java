package app.charka.encounter;

import app.charka.attack.AttackForm;
import app.charka.item.ItemAction;
import app.charka.item.ItemSummary;
import app.charka.model.Character;
import app.charka.model.Encounter;
import app.charka.model.EncounterParticipant;
import app.charka.model.Item;
import app.charka.model.PendingBlast;

import java.util.List;

/**
 * Страница идущего боя.
 *
 * @param current    чей сейчас ход; null — ходить некому
 * @param lastInRound следующий ход начнёт новый раунд
 * @param rows       участники в порядке хода
 * @param addable    кого ещё можно ввести в бой
 */
public record EncounterView(Encounter encounter, Character current, boolean lastInRound, List<Row> rows, List<Character> addable) {

    /**
     * @param targets цели атаки: живые участники, сначала другая сторона
     */
    public record Row(EncounterParticipant participant, Character character, CombatCard card, boolean current,
                      List<Weapon> weapons, List<Character> targets, List<PendingBlast> blasts) {

        /** Итог действия с этим предметом показывается в этой строке */
        public boolean hasItem(Long itemId) {
            return weapons.stream().anyMatch(weapon -> weapon.item().getId().equals(itemId));
        }
    }

    /**
     * @param url адрес предмета, от него строятся адреса действий
     */
    public record Weapon(Item item, ItemSummary summary, AttackForm form, List<ItemAction> actions, String url) {
    }

    /** Кого можно взять в бой при его начале */
    public record Candidate(Character character, CombatCard card) {
    }
}
