package app.charka.service;

import app.charka.model.Campaign;
import app.charka.model.Character;

import java.time.LocalDate;
import java.util.List;

public interface CharacterService {
    Character createInCampaign(Long campaignId, Character character);
    Character getById(Long id);
    List<Character> getCampaignCharacters(Campaign campaign);
    List<Character> getAll();
    Character rename(Long id, String newName);
    Character changeBirthDate(Long id, LocalDate newBirthDate);
    void delete(Long id);
}
