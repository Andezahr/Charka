package app.charka.service.campaign;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Inventory;
import app.charka.model.Item;
import app.charka.model.notes.Note;
import app.charka.model.notes.NoteCategory;
import app.charka.repository.CampaignRepository;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Импорт кампании из JSON экспорта (U-04): новая кампания со всеми персонажами, их предметами, счётчиками, навыками,
 * заметками, деньгами и хроникой. Id из файла не используются; ссылки предметов друг на друга (программа в деке,
 * опция в киберконечности) переводятся на новые id по списку ключей системы.
 */
@Service
@RequiredArgsConstructor
public class CampaignImportService {

    private final ObjectMapper objectMapper;
    private final CampaignRepository campaignRepository;
    private final GameSystemRegistry systemRegistry;
    private final EntityManager entityManager;
    private final app.charka.security.AccessService accessService;

    /**
     * @return новая кампания
     * @throws IllegalArgumentException если файл не прочитать или системы кампании нет
     */
    @Transactional
    public Campaign importCampaign(String json) {
        JsonNode tree;
        try {
            tree = objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Файл не похож на JSON экспорта кампании");
        }
        if (tree == null || !tree.isObject() || !tree.has("name")) {
            throw new IllegalArgumentException("Файл не похож на JSON экспорта кампании");
        }
        // Категории заметок связаны с заметками в обе стороны: их собираем сами, по названию
        Map<String, List<List<String>>> categoriesByCharacter = new HashMap<>();
        List<List<List<String>>> noteCategories = new ArrayList<>();
        for (JsonNode character : iterable(tree.get("characters"))) {
            List<List<String>> perNote = new ArrayList<>();
            for (JsonNode note : iterable(character.get("notes"))) {
                List<String> names = new ArrayList<>();
                iterable(note.get("categories")).forEach(category -> {
                    if (category.hasNonNull("name")) {
                        names.add(category.get("name").asText());
                    }
                });
                perNote.add(names);
                ((ObjectNode) note).remove("categories");
            }
            noteCategories.add(perNote);
        }
        Campaign campaign;
        try {
            campaign = objectMapper.copy()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .treeToValue(tree, Campaign.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось прочитать кампанию: " + e.getOriginalMessage());
        }
        GameSystem system = systemRegistry.find(campaign.getSystem())
                .orElseThrow(() -> new IllegalArgumentException("Неизвестная игровая система: " + campaign.getSystem()));

        Map<String, Item> itemsByOldId = new HashMap<>();
        campaign.setId(null);
        // мастер не экспортируется: импортировавший сам становится мастером
        accessService.current().ifPresent(campaign::setGm);
        campaign.getChronicles().forEach(chronicle -> {
            chronicle.setId(null);
            chronicle.setCampaign(campaign);
        });
        campaign.getMaps().forEach(map -> {
            map.setId(null);
            map.setCampaign(campaign);
            app.charka.map.MapDoc.parse(map.getDoc());
        });
        for (int index = 0; index < campaign.getCharacters().size(); index++) {
            Character character = campaign.getCharacters().get(index);
            character.setId(null);
            character.setCampaign(campaign);
            character.getWounds().forEach(wound -> wound.setId(null));
            character.getMoneyList().forEach(money -> money.setId(null));
            character.getRecurringExpenses().forEach(expense -> expense.setId(null));
            character.getEffects().forEach(effect -> effect.setId(null));
            character.getContacts().forEach(contact -> contact.setId(null));
            character.getCounters().forEach(counter -> counter.setId(null));
            character.getSkills().forEach(skill -> skill.setId(null));
            for (Inventory inventory : character.getInventories()) {
                inventory.setId(null);
                for (Item item : inventory.getContent()) {
                    if (item.getId() != null) {
                        itemsByOldId.put(String.valueOf(item.getId()), item);
                    }
                    item.setId(null);
                }
            }
            Map<String, NoteCategory> categories = new HashMap<>();
            List<List<String>> perNote = index < noteCategories.size() ? noteCategories.get(index) : List.of();
            for (int noteIndex = 0; noteIndex < character.getNotes().size(); noteIndex++) {
                Note note = character.getNotes().get(noteIndex);
                note.setId(null);
                Set<NoteCategory> linked = new HashSet<>();
                for (String name : noteIndex < perNote.size() ? perNote.get(noteIndex) : List.<String>of()) {
                    linked.add(categories.computeIfAbsent(name, key -> {
                        NoteCategory category = new NoteCategory();
                        category.setName(key);
                        category.setCharacter(character);
                        return category;
                    }));
                }
                note.setCategories(linked);
            }
        }
        Campaign saved = campaignRepository.save(campaign);
        entityManager.flush();

        // Ссылки предметов друг на друга: старый id → новый
        Set<String> referenceKeys = system.getItemReferenceKeys();
        if (!referenceKeys.isEmpty()) {
            saved.getCharacters().stream()
                    .flatMap(character -> character.getInventories().stream())
                    .flatMap(inventory -> inventory.getContent().stream())
                    .forEach(item -> referenceKeys.forEach(key -> {
                        String old = item.getProperties().get(key);
                        if (old == null) {
                            return;
                        }
                        Item target = itemsByOldId.get(old);
                        if (target == null || target.getId() == null) {
                            item.getProperties().remove(key);
                        } else {
                            item.getProperties().put(key, String.valueOf(target.getId()));
                        }
                    }));
        }
        return saved;
    }

    private static Iterable<JsonNode> iterable(JsonNode node) {
        return node instanceof ArrayNode array ? array : List.of();
    }
}
