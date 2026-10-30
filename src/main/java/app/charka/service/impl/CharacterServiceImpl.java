package app.charka.service.impl;

import app.charka.exception.CharacterNotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Inventory;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.service.InventoryService;
import app.charka.service.campaign.CampaignService;
import app.charka.service.counter.CounterService;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CharacterServiceImpl implements CharacterService {

    private final CharacterRepository characterRepository;
    private final CampaignService campaignService;
    private final InventoryService inventoryService;
    private final CounterService counterService;
    private final GameSystemRegistry systemRegistry;

    /**
     * Создаёт нового персонажа в заданной кампании, выдаёт базовый инвентарь
     * и счётчики по умолчанию системы кампании (для CP2020 — характеристики, броня, здоровье).
     */
    @Override
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

    @Override
    public Character getById(Long id) {
        return characterRepository.findById(id)
                .orElseThrow(CharacterNotFoundException.forId(id));
    }

    @Override
    public List<Character> getCampaignCharacters(Campaign campaign) {
        return characterRepository
                .findByCampaign(campaign);
    }

    @Override
    public List<Character> getAll() {
        return characterRepository.findAll();
    }

    @Override
    public Character rename(Long id, String newName) {
        var character = characterRepository.findById(id)
                .orElseThrow(CharacterNotFoundException.forId(id));
        character.setName(newName);
        return characterRepository.save(character);
    }

    @Override
    public Character changeBirthDate(Long id, LocalDate newBirthDate) {
        var character = characterRepository.findById(id)
                .orElseThrow(CharacterNotFoundException.forId(id));
        character.setBirthDate(newBirthDate);
        return characterRepository.save(character);
    }

    @Override
    public void delete(Long id) {
        characterRepository.deleteById(id);
    }
}
