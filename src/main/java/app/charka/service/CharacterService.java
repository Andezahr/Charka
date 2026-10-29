package app.charka.service;

import app.charka.exception.CharacterNotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Inventory;
import app.charka.repository.CharacterRepository;
import app.charka.service.campaign.CampaignService;
import app.charka.service.counter.CounterService;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CharacterService  {

    private final CharacterRepository characterRepository;
    private final CampaignService campaignService;
    private final InventoryService inventoryService;
    private final CounterService counterService;
    private final GameSystemRegistry systemRegistry;

    /**
     * Создаёт нового персонажа в заданной кампании, выдаёт базовый инвентарь
     * и счётчики по умолчанию системы кампании (для CP2020 — характеристики, броня, здоровье).
     */
    @Transactional
    public Character createInCampaign(Long campaignId, Character character) {
        Campaign campaign = campaignService.getById(campaignId);
        character.setCampaign(campaign);
        Character saved = characterRepository.save(character);
        Inventory inventory = new Inventory();
        inventory.setName("Backpack");
        inventoryService.create(character.getId(), inventory);
        counterService.createDefaults(saved.getId(),
                systemRegistry.forCharacter(saved).getDefaultCounterTypes());
        return saved;
    }

    public Character getById(Long id) {
        return characterRepository.findById(id)
                .orElseThrow(CharacterNotFoundException.forId(id));
    }

    public List<Character> getCampaignCharacters(Campaign campaign) {
        return characterRepository
                .findByCampaign(campaign);
    }

    public List<Character> getAll() {
        return characterRepository.findAll();
    }

    @Transactional
    public Character rename(Long id, String newName) {
        var character = characterRepository.findById(id)
                .orElseThrow(CharacterNotFoundException.forId(id));
        character.setName(newName);
        return characterRepository.save(character);
    }

    @Transactional
    public Character changeBirthDate(Long id, LocalDate newBirthDate) {
        var character = characterRepository.findById(id)
                .orElseThrow(CharacterNotFoundException.forId(id));
        character.setBirthDate(newBirthDate);
        return characterRepository.save(character);
    }

    @Transactional
    public void delete(Long id) {
        characterRepository.deleteById(id);
    }
}
