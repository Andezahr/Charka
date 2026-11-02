package app.charka.api;

import app.charka.map.BattleFieldService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Поле боя для браузера: JSON карты и фишек (опрос с ETag, {@link MapEtagFilter}), ход фишки, досягаемость, линия взгляда,
 * двери, указатели. Ошибки — текстом ({@code ApiExceptionHandler}).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/campaign/{campaignId}/encounter/map")
public class BattleFieldController {

    private final BattleFieldService battleFieldService;

    /** 404 — боя нет или он без карты */
    @GetMapping
    public ResponseEntity<BattleFieldService.Field> field(@PathVariable Long campaignId) {
        return battleFieldService.field(campaignId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/participants/{participantId}/move")
    public ResponseEntity<Void> move(@PathVariable Long campaignId, @PathVariable Long participantId,
                                     @RequestParam int x, @RequestParam int y) {
        battleFieldService.move(campaignId, participantId, x, y);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/participants/{participantId}/lift")
    public ResponseEntity<Void> lift(@PathVariable Long campaignId, @PathVariable Long participantId) {
        battleFieldService.lift(campaignId, participantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/participants/{participantId}/reach")
    public BattleFieldService.Reach reach(@PathVariable Long campaignId, @PathVariable Long participantId) {
        return battleFieldService.reach(campaignId, participantId);
    }

    @GetMapping("/sight")
    public BattleFieldService.Sight sight(@PathVariable Long campaignId, @RequestParam Long from, @RequestParam Long to) {
        return battleFieldService.sight(campaignId, from, to);
    }

    @PostMapping("/door")
    public ResponseEntity<Void> door(@PathVariable Long campaignId, @RequestParam String edge) {
        battleFieldService.toggleDoor(campaignId, edge);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/pointer")
    public ResponseEntity<Void> pointer(@PathVariable Long campaignId, @RequestParam int x, @RequestParam int y,
                                        @RequestParam(required = false) Integer fromX, @RequestParam(required = false) Integer fromY,
                                        @RequestParam(required = false) String who) {
        battleFieldService.point(campaignId, x, y, fromX, fromY, who);
        return ResponseEntity.noContent().build();
    }

    /** Мастер правит копию карты боя: документ целиком */
    @PostMapping(value = "/doc", consumes = "application/json")
    public ResponseEntity<Void> doc(@PathVariable Long campaignId, @RequestBody String json) {
        battleFieldService.updateDoc(campaignId, json);
        return ResponseEntity.noContent().build();
    }
}
