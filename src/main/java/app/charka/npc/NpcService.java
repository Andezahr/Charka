package app.charka.npc;

import app.charka.exception.CharacterNotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.CharacterSkill;
import app.charka.model.Inventory;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.CounterStrategyFactory;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
import app.charka.service.campaign.CampaignService;
import app.charka.service.counter.CounterService;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * NPC кампании: создание, по шаблону системы или пустым, копии и удаление.
 */
@Service
@RequiredArgsConstructor
public class NpcService {

    private static final int MAX_AT_ONCE = 20;
    private static final String DEFAULT_NAME = "NPC";

    private final CampaignService campaignService;
    private final CharacterService characterService;
    private final CharacterRepository characterRepository;
    private final CounterService counterService;
    private final CounterStrategyFactory strategyFactory;
    private final ItemService itemService;
    private final GameSystemRegistry systemRegistry;
    private final DiceRoller diceRoller;

    /**
     * Каждый NPC бросается отдельно, поэтому двое по одному шаблону различаются.
     *
     * @param templateCode шаблон системы; пусто — пустой NPC
     * @param name         имя; пусто — название шаблона. Если создаётся несколько, к нему добавляется номер
     * @param advanced     продвинутый NPC, если система это различает
     * @throws IllegalArgumentException неизвестный шаблон или неверное число
     */
    @Transactional
    public List<Character> create(Long campaignId, String templateCode, String name, int count, boolean advanced) {
        checkCount(count);
        Campaign campaign = campaignService.getById(campaignId);
        GameSystem system = systemRegistry.get(campaign.getSystem());
        boolean fromTemplate = templateCode != null && !templateCode.isBlank();
        String base = baseName(system, templateCode, fromTemplate, name);

        Set<String> taken = takenNames(campaign);
        List<Character> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            NpcSheet sheet = fromTemplate ? system.rollNpc(templateCode, advanced, diceRoller) : null;
            String npcName = count == 1 && !taken.contains(base) ? base : nextName(base, taken, 1);
            taken.add(npcName);
            Character npc = newNpc(campaignId, npcName);
            if (sheet != null) {
                apply(npc, sheet);
            }
            created.add(npc);
        }
        return created;
    }

    /**
     * Копии с теми же статами, навыками и снаряжением, без ран; деньги, заметки и журнал ран не копируются.
     *
     * @throws IllegalArgumentException если это не NPC или неверное число
     */
    @Transactional
    public List<Character> copy(Long npcId, int count) {
        checkCount(count);
        Character source = requireNpc(npcId);
        String base = source.getName().replaceFirst("\\s+\\d+$", "");
        Set<String> taken = takenNames(source.getCampaign());
        List<Character> copies = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = nextName(base, taken, 2);
            taken.add(name);
            Character copy = newNpc(source.getCampaign().getId(), name);
            copy.setBirthDate(source.getBirthDate());
            copy.getRoles().addAll(source.getRoles());
            copySkills(source, copy);
            copyCounters(source, copy);
            copyInventories(source, copy);
            copies.add(copy);
        }
        return copies;
    }

    /**
     * Удаляет только NPC: у персонажей игроков есть что терять.
     *
     * @return кампания, в которой он был
     * @throws IllegalArgumentException если это не NPC
     */
    @Transactional
    public Long delete(Long npcId) {
        Character npc = requireNpc(npcId);
        Long campaignId = npc.getCampaign().getId();
        // коллекция кампании иначе вернёт его обратно каскадом при сохранении
        if (npc.getCampaign().getCharacters() != null) {
            npc.getCampaign().getCharacters().removeIf(character -> character == npc);
        }
        characterRepository.delete(npc);
        return campaignId;
    }

    private Character newNpc(Long campaignId, String name) {
        Character npc = new Character();
        npc.setName(name);
        npc.setNpc(true);
        return characterService.createInCampaign(campaignId, npc);
    }

    private void apply(Character npc, NpcSheet sheet) {
        npc.getRoles().addAll(sheet.roles());
        sheet.skills().forEach((code, level) -> addSkill(npc, code, level, null));
        for (Counter counter : npc.getCounters()) {
            Map<String, Integer> attributes = sheet.attributes().get(counter.getType());
            if (attributes != null) {
                counterService.updateAttributes(npc.getId(), counter.getId(), attributes);
            }
        }
        Long inventoryId = npc.getInventories().get(0).getId();
        for (NpcSheet.Gear gear : sheet.items()) {
            Item item = itemService.addFromCatalog(inventoryId, gear.catalogKey(), 1);
            if (gear.action() != null) {
                itemService.performAction(npc.getId(), item.getId(), gear.action(), Map.of());
            }
        }
    }

    private static void addSkill(Character character, String code, int level, String customName) {
        CharacterSkill skill = new CharacterSkill();
        skill.setCharacter(character);
        skill.setSkillCode(code);
        skill.setLevel(level);
        skill.setCustomName(customName);
        character.getSkills().add(skill);
    }

    private static void copySkills(Character source, Character copy) {
        source.getSkills().forEach(skill -> addSkill(copy, skill.getSkillCode(), skill.getLevel(), skill.getCustomName()));
    }

    /** Счётчик того же типа у копии уже мог создаться по умолчанию: тогда значения переносятся в него */
    private void copyCounters(Character source, Character copy) {
        List<Counter> own = new ArrayList<>(copy.getCounters());
        for (Counter counter : source.getCounters()) {
            Counter target = own.stream().filter(candidate -> candidate.getType().equals(counter.getType())).findFirst().orElse(null);
            if (target == null) {
                target = new Counter();
                target.setType(counter.getType());
                target.setCharacter(copy);
                copy.getCounters().add(target);
            } else {
                own.remove(target);
            }
            target.setName(counter.getName());
            target.setValue(counter.getValue());
            target.getAttributes().clear();
            target.getAttributes().putAll(counter.getAttributes());
            Counter copied = target;
            strategyFactory.findStrategy(counter.getType()).ifPresent(strategy -> strategy.prepareCopy(copied));
        }
    }

    /** Первый инвентарь копии уже создан вместе с ней */
    private void copyInventories(Character source, Character copy) {
        GameSystem system = systemRegistry.forCharacter(copy);
        for (int i = 0; i < source.getInventories().size(); i++) {
            Inventory from = source.getInventories().get(i);
            Inventory to;
            if (i < copy.getInventories().size()) {
                to = copy.getInventories().get(i);
            } else {
                to = new Inventory();
                to.setCharacter(copy);
                copy.getInventories().add(to);
            }
            to.setName(from.getName());
            for (Item item : from.getContent()) {
                Item copied = copyItem(item, to);
                system.prepareItemCopy(copied);
                to.getContent().add(copied);
            }
        }
    }

    private static Item copyItem(Item item, Inventory inventory) {
        Item copy = new Item();
        copy.setName(item.getName());
        copy.setQuantity(item.getQuantity());
        copy.setCost(item.getCost());
        copy.setDescription(item.getDescription());
        copy.setType(item.getType());
        copy.getProperties().putAll(item.getProperties());
        copy.setInventory(inventory);
        return copy;
    }

    private Character requireNpc(Long id) {
        Character character = characterRepository.findById(id).orElseThrow(CharacterNotFoundException.forId(id));
        if (!character.isNpc()) {
            throw new IllegalArgumentException("Это персонаж игрока, а не NPC: " + character.getName());
        }
        return character;
    }

    private static String baseName(GameSystem system, String templateCode, boolean fromTemplate, String name) {
        if (name != null && !name.isBlank()) {
            return name.strip();
        }
        if (!fromTemplate) {
            return DEFAULT_NAME;
        }
        return system.getNpcTemplates().stream()
                .filter(template -> template.code().equals(templateCode))
                .map(NpcTemplate::label)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Нет такого шаблона NPC: " + templateCode));
    }

    private Set<String> takenNames(Campaign campaign) {
        return characterRepository.findByCampaign(campaign).stream().map(Character::getName).collect(Collectors.toCollection(HashSet::new));
    }

    private static String nextName(String base, Set<String> taken, int from) {
        int number = from;
        while (taken.contains(base + " " + number)) {
            number++;
        }
        return base + " " + number;
    }

    private static void checkCount(int count) {
        if (count < 1 || count > MAX_AT_ONCE) {
            throw new IllegalArgumentException("Сколько создать за раз: от 1 до " + MAX_AT_ONCE);
        }
    }
}
