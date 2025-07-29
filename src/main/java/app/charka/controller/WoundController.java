package app.charka.controller;

import app.charka.model.Wound;
import app.charka.service.WoundService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/character/{characterId}/wounds")
public class WoundController {

    private final WoundService woundService;

    public WoundController(WoundService woundService) {
        this.woundService = woundService;
    }

    @PostMapping
    public String addWound(
            @PathVariable Long characterId,
            @RequestParam String name,
            @RequestParam(required = false) Long severity
    ) {
        Wound newWound = new Wound();
        newWound.setName(name);
        if (severity != null) newWound.setSeverity(severity);
        woundService.create(characterId, newWound);
        return "redirect:/character/" + characterId;
    }

    @PostMapping("/{woundId}/delete")
    public String deleteWound(@PathVariable Long characterId, @PathVariable Long woundId) {
        Wound wound = woundService.getById(woundId);
        if (wound.getCharacter().getId().equals(characterId)) {
            woundService.delete(wound.getId());
        }
        return "redirect:/character/" + characterId;
    }

}
