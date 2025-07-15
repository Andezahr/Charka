package app.Charka.controller;

import app.Charka.Routes;
import app.Charka.model.Character;
import app.Charka.model.Wound;
import app.Charka.repository.CharacterRepository;
import app.Charka.repository.WoundRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class WoundController {

    private final CharacterRepository characterRepository;
    private final WoundRepository woundRepository;

    public WoundController(CharacterRepository characterRepository, WoundRepository woundRepository) {
        this.characterRepository = characterRepository;
        this.woundRepository = woundRepository;
    }

    @PostMapping(Routes.WOUNDS_ADD)
    public String addWound(
            @PathVariable Long characterId,
            @RequestParam String name,
            @RequestParam(required = false) Long severity // если поле необязательное
    ) {

        Character character = characterRepository.findById(characterId).orElseThrow();
        Wound newWound = new Wound();
        newWound.setCharacter(character);
        newWound.setName(name);
        if (severity != null) newWound.setSeverity(severity);
        woundRepository.save(newWound);
        return Routes.CHARACTER_REDIRECT + characterId;
    }

    @PostMapping(Routes.WOUNDS_DELETE)
    public String deleteWound(@PathVariable Long characterId, @PathVariable Long woundId) {
        Wound wound = woundRepository.findById(woundId).orElseThrow();
        if (wound.getCharacter().getId().equals(characterId)) {
            woundRepository.delete(wound);
        }
        return Routes.CHARACTER_REDIRECT + characterId;
    }

}
