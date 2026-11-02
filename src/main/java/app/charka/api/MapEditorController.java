package app.charka.api;

import app.charka.map.BattleMapService;
import app.charka.map.MapDoc;
import app.charka.map.MapObject;
import app.charka.map.MapRules;
import app.charka.map.Material;
import app.charka.model.BattleMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Редактор карты (M-06): документ и правила системы для браузера, автосохранение документа целиком.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/campaign/{campaignId}/maps/{mapId}/doc")
public class MapEditorController {

    private final BattleMapService battleMapService;

    /**
     * @param defaults материал по умолчанию у инструмента
     */
    public record Editable(Long id, String name, MapDoc doc, String metric, List<Material> materials, List<MapObject> objects,
                           Map<String, String> defaults) {
    }

    @GetMapping
    public Editable get(@PathVariable Long campaignId, @PathVariable Long mapId) {
        BattleMap map = battleMapService.get(campaignId, mapId);
        MapRules rules = battleMapService.rules(campaignId);
        return new Editable(map.getId(), map.getName(), MapDoc.parse(map.getDoc()), rules.metric(), rules.materials(), rules.objects(), rules.defaults());
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Void> save(@PathVariable Long campaignId, @PathVariable Long mapId, @RequestBody String json) {
        battleMapService.updateDoc(campaignId, mapId, json);
        return ResponseEntity.noContent().build();
    }
}
