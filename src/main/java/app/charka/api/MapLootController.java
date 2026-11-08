package app.charka.api;

import app.charka.map.MapLootService;
import app.charka.model.EncounterParticipant;
import app.charka.repository.EncounterRepository;
import app.charka.security.AccessService;
import app.charka.security.GmMode;
import app.charka.service.campaign.CampaignService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * KF-05: предметы на карте боя. Положить и убрать — мастер ({@code AccessInterceptor}: «/encounter/loot» только мастеру),
 * подобрать — игрок за своего персонажа или мастер. Ответ — текст итога, ошибки — текстом ({@code ApiExceptionHandler}).
 * Не под «/encounter/map»: подбор меняет инвентарь, его можно отменить (A-01).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/campaign/{campaignId}/encounter/loot", produces = "text/plain;charset=UTF-8")
public class MapLootController {

    private final MapLootService lootService;
    private final AccessService accessService;
    private final CampaignService campaignService;
    private final EncounterRepository encounterRepository;

    @PostMapping
    public ResponseEntity<String> place(@PathVariable Long campaignId, @RequestParam String key,
                                        @RequestParam(defaultValue = "1") int quantity, @RequestParam int x, @RequestParam int y,
                                        @RequestParam(defaultValue = "0") int level, HttpServletRequest request) {
        if (GmMode.npcOnly(request)) {
            throw new IllegalArgumentException("Предмет на карту кладут в режиме «Бог»");
        }
        return ResponseEntity.ok(lootService.place(campaignId, key, quantity, x, y, level));
    }

    @PostMapping("/{lootId}/take")
    public ResponseEntity<String> take(@PathVariable Long campaignId, @PathVariable Long lootId, @RequestParam Long participant) {
        EncounterParticipant who = encounterRepository.findByCampaignId(campaignId)
                .flatMap(encounter -> encounter.getParticipants().stream().filter(p -> p.getId().equals(participant)).findFirst())
                .orElseThrow(() -> new IllegalArgumentException("Такого участника в бою нет"));
        if (!accessService.canControl(who.getCharacter())) {
            throw new IllegalArgumentException("Подбирать можно только своим персонажем");
        }
        return ResponseEntity.ok(lootService.take(campaignId, lootId, participant));
    }

    @PostMapping("/{lootId}/remove")
    public ResponseEntity<String> remove(@PathVariable Long campaignId, @PathVariable Long lootId) {
        if (!accessService.isGm(campaignService.getById(campaignId))) {
            throw new IllegalArgumentException("Убрать предмет с карты может только мастер");
        }
        lootService.remove(campaignId, lootId);
        return ResponseEntity.ok("Убрано с карты");
    }
}
