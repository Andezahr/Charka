package app.charka.controller;

import app.charka.Routes;
import app.charka.model.Character;
import app.charka.model.Wound;
import app.charka.repository.CharacterRepository;
import app.charka.repository.WoundRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
