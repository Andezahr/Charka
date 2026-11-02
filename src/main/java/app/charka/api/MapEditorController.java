package app.charka.api;

import app.charka.map.BattleMapService;
import app.charka.map.MapDoc;
import app.charka.map.EditableMap;
import app.charka.model.BattleMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Редактор карты (M-06): документ и правила системы для браузера, автосохранение документа целиком.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/campaign/{campaignId}/maps/{mapId}/doc")
public class MapEditorController {

    private final BattleMapService battleMapService;

    @GetMapping
    public EditableMap get(@PathVariable Long campaignId, @PathVariable Long mapId) {
        BattleMap map = battleMapService.get(campaignId, mapId);
        return EditableMap.of(map.getId(), map.getName(), MapDoc.parse(map.getDoc()), battleMapService.rules(campaignId));
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Void> save(@PathVariable Long campaignId, @PathVariable Long mapId, @RequestBody String json) {
        battleMapService.updateDoc(campaignId, mapId, json);
        return ResponseEntity.noContent().build();
    }
}
