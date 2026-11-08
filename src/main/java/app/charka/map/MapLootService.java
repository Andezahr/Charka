package app.charka.map;

import app.charka.attack.AttackSource;
import app.charka.exception.NotFoundException;
import app.charka.item.CatalogItem;
import app.charka.model.Character;
import app.charka.model.Encounter;
import app.charka.model.EncounterParticipant;
import app.charka.model.Inventory;
import app.charka.model.Item;
import app.charka.model.MapLoot;
import app.charka.repository.EncounterRepository;
import app.charka.repository.MapLootRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

/**
 * KF-05: предметы на карте идущего боя. Мастер кладёт строку справочника системы на клетку; персонаж на ней или рядом
 * подбирает одним нажатием — предмет ложится в носимый инвентарь, оружие становится основным и сразу есть в меню атаки.
 * Ядро: работает в любой системе со справочником, правила подбора одни.
 */
@Service
@RequiredArgsConstructor
public class MapLootService {

    /** HOMEBREW: подобрать можно со своей клетки или соседней, по диагонали тоже; действием не считается — решает мастер */
    public static final int REACH_CELLS = 1;

    private final MapLootRepository lootRepository;
    private final EncounterRepository encounterRepository;
    private final GameSystemRegistry systemRegistry;
    private final ItemService itemService;
    private final CharacterService characterService;

    /**
     * Положить на клетку.
     *
     * @param key      ключ справочника системы кампании
     * @param quantity сколько строк справочника (у патронов — пачек)
     * @return что положено: «Нож на карте»
     * @throws IllegalArgumentException боя или карты нет, клетка вне карты, такой строки в справочнике нет
     */
    @Transactional
    public String place(Long campaignId, String key, int quantity, int x, int y, int level) {
        Encounter encounter = withMap(campaignId);
        MapDoc doc = MapDoc.parse(encounter.getMapDoc());
        if (!doc.inside(x, y) || level < 0 || (level > 0 && !doc.hasLevel(level))) {
            throw new IllegalArgumentException("Клетка вне карты");
        }
        CatalogItem entry = systemRegistry.get(encounter.getCampaign().getSystem()).getItemCatalog().stream()
                .filter(candidate -> candidate.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Такого предмета в справочнике нет: " + key));
        MapLoot loot = new MapLoot();
        loot.setEncounterId(encounter.getId());
        loot.setPosX(x);
        loot.setPosY(y);
        loot.setPosLevel(level);
        loot.setCatalogKey(entry.key());
        loot.setName(entry.nameInInventory());
        loot.setQuantity(Math.max(1, quantity));
        lootRepository.save(loot);
        return loot.getName() + " на карте";
    }

    /**
     * Подобрать: персонаж на этой клетке или соседней, на том же уровне.
     *
     * @return итог: «Зэк подбирает Нож — основное оружие»
     * @throws IllegalArgumentException далеко, персонаж не на карте, некуда положить
     */
    @Transactional
    public String take(Long campaignId, Long lootId, Long participantId) {
        Encounter encounter = withMap(campaignId);
        MapLoot loot = loot(encounter, lootId);
        EncounterParticipant participant = encounter.getParticipants().stream()
                .filter(p -> p.getId().equals(participantId)).findFirst()
                .orElseThrow(NotFoundException.forId("EncounterParticipant", participantId));
        if (participant.getPosX() == null) {
            throw new IllegalArgumentException(participant.getCharacter().getName() + " не на карте");
        }
        int cells = Math.max(Math.abs(participant.getPosX() - loot.getPosX()), Math.abs(participant.getPosY() - loot.getPosY()));
        if (participant.getPosLevel() != loot.getPosLevel() || cells > REACH_CELLS) {
            throw new IllegalArgumentException("Далеко: подойдите к «" + loot.getName() + "» вплотную");
        }
        Character character = participant.getCharacter();
        Inventory inventory = character.getInventories().stream()
                .sorted(Comparator.comparing((Inventory candidate) -> !candidate.isCarried()).thenComparing(Inventory::getId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Некуда положить: у " + character.getName() + " нет инвентаря"));
        Item item = itemService.addFromCatalog(inventory.getId(), loot.getCatalogKey(), loot.getQuantity());
        lootRepository.delete(loot);
        String result = character.getName() + " подбирает «" + loot.getName() + "»";
        if (isWeapon(character, item)) {
            characterService.setMainWeapon(character.getId(), item.getId());
            result += " — основное оружие";
        }
        return result;
    }

    /** Мастер убирает с карты */
    @Transactional
    public void remove(Long campaignId, Long lootId) {
        Encounter encounter = withMap(campaignId);
        lootRepository.delete(loot(encounter, lootId));
    }

    private boolean isWeapon(Character character, Item item) {
        GameSystem system = systemRegistry.forCharacter(character);
        try {
            return system.getAttackRules() != null
                    && system.getAttackRules().form(AttackSource.of(item), system.getCurrentStats(character)) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private MapLoot loot(Encounter encounter, Long lootId) {
        return lootRepository.findById(lootId)
                .filter(candidate -> candidate.getEncounterId().equals(encounter.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Этого предмета на карте уже нет"));
    }

    private Encounter withMap(Long campaignId) {
        Encounter encounter = encounterRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Боя нет"));
        if (encounter.getMapDoc() == null) {
            throw new IllegalArgumentException("Бой идёт без карты");
        }
        return encounter;
    }
}
