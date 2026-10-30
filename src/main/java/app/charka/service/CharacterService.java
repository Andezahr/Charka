package app.charka.service;

import app.charka.model.Campaign;
import app.charka.model.Character;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface CharacterService {

    @Transactional
    Character createInCampaign(Long campaignId, Character character);

    Character getById(Long id);

    List<Character> getCampaignCharacters(Campaign campaign);

    List<Character> getAll();

    @Transactional
    Character rename(Long id, String newName);

    @Transactional
    Character changeBirthDate(Long id, LocalDate newBirthDate);

    @Transactional
    void delete(Long id);
}
