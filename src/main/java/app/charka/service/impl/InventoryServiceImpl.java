package app.charka.service.impl;

import app.charka.exception.CharacterNotFoundException;
import app.charka.exception.InventoryNotFoundException;
import app.charka.model.Inventory;
import app.charka.model.Item;
import app.charka.repository.CharacterRepository;
import app.charka.repository.InventoryRepository;
import app.charka.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final CharacterRepository characterRepository;

    /**
     * Получить все инвентари, принадлежащие указанному персонажу.
     *
     * @param characterId идентификатор персонажа
     * @return список Inventory; пустой список, если персонаж не найден или у него нет инвентарей
     */
    @Override
    public List<Inventory> getByCharacter(Long characterId) {
        return inventoryRepository.findByCharacterId(characterId);
    }

    @Override
    public Inventory getById(Long id) {
        return inventoryRepository.findById(id)
                .orElseThrow(InventoryNotFoundException.forId(id));
    }

    /**
     * Создать новый инвентарь для указанного персонажа.
     *
     * @param characterId ID персонажа
     * @param inventory   объект Inventory с заполненным полем name
     * @return сохраненный Inventory с присвоенным id и ссылкой на Character
     * @throws IllegalArgumentException если персонаж не найден
     */
    @Override
    public Inventory create(Long characterId, Inventory inventory) {
        inventory.setCharacter(
                characterRepository.findById(characterId)
                .orElseThrow(
                        CharacterNotFoundException.forId(characterId)
                ));
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
    @Override
    public Inventory update(Long id, Inventory updatedInventory) {
        Inventory existing = inventoryRepository.findById(id)
                .orElseThrow(InventoryNotFoundException.forId(id));

        existing.setName(updatedInventory.getName());
        return inventoryRepository.save(existing);
    }

    @Override
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
    @Override
    public List<Item> getContent(Long id) {
        Inventory inventory = inventoryRepository.findById(id)
                .orElseThrow(InventoryNotFoundException.forId(id));
        return inventory.getContent();
    }
}
