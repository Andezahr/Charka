package app.charka.service;
import app.charka.exception.InventoryNotFoundException;
import app.charka.model.Inventory;
import app.charka.model.Item;
import app.charka.model.Character;
import app.charka.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    /**
     * Получить все инвентари, принадлежащие указанному персонажу.
     *
     * @param characterId идентификатор персонажа
     * @return список Inventory; пустой список, если персонаж не найден или у него нет инвентарей
     */
    @Transactional(readOnly = true)
    public List<Inventory> getByCharacter(Long characterId) {
        return inventoryRepository.findByCharacterId(characterId);
    }

    @Transactional(readOnly = true)
    public Inventory getById(Long id) {
        return inventoryRepository.findById(id)
                .orElseThrow(InventoryNotFoundException.forId(id));
    }

    /**
     * Создать новый инвентарь для указанного персонажа.
     *
     * @param character  персонаж
     * @param inventory   объект Inventory с заполненным полем name
     * @return сохраненный Inventory с присвоенным id и ссылкой на Character
     * @throws IllegalArgumentException если персонаж не найден
     */
    @Transactional
    public Inventory create(Character character, Inventory inventory) {
        inventory.setCharacter(character);
        return inventoryRepository.save(inventory);
    }

    /**
     * Обновить имя существующего инвентаря.
     *
     * @param id              идентификатор Inventory для обновления
     * @param updatedInventory объект Inventory, содержащий новое имя
     * @return обновленный и сохраненный Inventory
     * @throws IllegalArgumentException если Inventory не найден
     */
    @Transactional
    public Inventory update(Long id, Inventory updatedInventory) {
        Inventory existing = inventoryRepository.findById(id)
                .orElseThrow(InventoryNotFoundException.forId(id));

        existing.setName(updatedInventory.getName());
        return inventoryRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        Inventory existing = inventoryRepository.findById(id)
                .orElseThrow(InventoryNotFoundException.forId(id));

        inventoryRepository.delete(existing);
    }

    /**
     * Получить список предметов (content) из указанного инвентаря.
     *
     * @param id идентификатор Inventory
     * @return список Item; пустой список, если Inventory не найден или пуст
     */
    @Transactional(readOnly = true)
    public List<Item> getContent(Long id) {
        Inventory inventory = inventoryRepository.findById(id)
                .orElseThrow(InventoryNotFoundException.forId(id));
        return inventory.getContent();
    }
}
