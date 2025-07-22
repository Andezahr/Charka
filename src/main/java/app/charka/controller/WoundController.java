package app.charka.controller;

import app.charka.Routes;
import app.charka.model.Wound;
import app.charka.service.WoundService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class WoundController {

    private final WoundService woundService;

    public WoundController(WoundService woundService) {
        this.woundService = woundService;
    }

    @PostMapping(Routes.WOUNDS_ADD)
    public String addWound(
            @PathVariable Long characterId,
            @RequestParam String name,
            @RequestParam(required = false) Long severity // если поле необязательное
    ) {
        Wound newWound = new Wound();
        newWound.setName(name);
        if (severity != null) newWound.setSeverity(severity);
        woundService.create(characterId, newWound);
        return Routes.CHARACTER_REDIRECT + characterId;
    }

    @PostMapping(Routes.WOUNDS_DELETE)
    public String deleteWound(@PathVariable Long characterId, @PathVariable Long woundId) {
        Wound wound = woundService.getById(woundId).orElseThrow();
        if (wound.getCharacter().getId().equals(characterId)) {
            woundService.delete(wound.getId());
        }
        return Routes.CHARACTER_REDIRECT + characterId;
    }

}
