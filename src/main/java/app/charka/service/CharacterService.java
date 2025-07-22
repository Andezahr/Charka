package app.charka.service;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Inventory;
import app.charka.repository.CharacterRepository;
import app.charka.service.campaign.CampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CharacterService  {

    private final CharacterRepository characterRepository;
    private final CampaignService campaignService;
    private final InventoryService inventoryService;

    /**
     * Создаёт нового персонажа в заданной кампании и выдаёт базовый инвентарь.
     */
    @Transactional
    public Character createInCampaign(Long campaignId, Character character) {
        var campaign = campaignService.getById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));
        character.setCampaign(campaign);
        var saved = characterRepository.save(character);
        Inventory inventory = new Inventory();
        inventory.setName("С собой");
        inventoryService.create(saved.getId(), inventory);
        return saved;
    }

    public Optional<Character> getById(Long id) {
        return characterRepository.findById(id);
    }

    public List<Character> getCampaignCharacters(Campaign campaign) {
        return characterRepository.findByCampaign(campaign);
    }

    public List<Character> getAll() {
        return characterRepository.findAll();
    }

    @Transactional
    public Character rename(Long id, String newName) {
        var character = characterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Character not found"));
        character.setName(newName);
        return characterRepository.save(character);
    }

    @Transactional
    public Character changeBirthDate(Long id, LocalDate newBirthDate) {
        var character = characterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Character not found"));
        character.setBirthDate(newBirthDate);
        return characterRepository.save(character);
    }

    @Transactional
    public void delete(Long id) {
        characterRepository.deleteById(id);
    }
}
