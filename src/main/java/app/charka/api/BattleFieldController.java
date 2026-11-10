package app.charka.api;

import app.charka.map.BattleFieldService;
import app.charka.security.GmMode;
import jakarta.servlet.http.HttpServletRequest;
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
    private final app.charka.security.AccessService accessService;
    private final app.charka.service.campaign.CampaignService campaignService;
    private final app.charka.attack.AttackService attackService;

    /** 404 — боя нет или он без карты; игроку — без фишек в тумане (M-16) */
    @GetMapping
    public ResponseEntity<BattleFieldService.Field> field(@PathVariable Long campaignId) {
        return battleFieldService.field(campaignId, viewer(campaignId)).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /** Подложка карты идущего боя — и игрокам (раздел «Карты» им закрыт); адрес с версией, кэш надолго */
    @GetMapping("/background")
    public ResponseEntity<byte[]> background(@PathVariable Long campaignId) {
        return battleFieldService.background(campaignId)
                .map(map -> ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.parseMediaType(map.getBackgroundType()))
                        .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(30)))
                        .body(map.getBackground()))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Мастер видит всё; игрок — глазами своих персонажей */
    private BattleFieldService.Viewer viewer(Long campaignId) {
        app.charka.model.Campaign campaign = campaignService.getById(campaignId);
        if (accessService.isGm(campaign)) {
            return BattleFieldService.Viewer.GM;
        }
        return new BattleFieldService.Viewer(campaign.getCharacters().stream().filter(accessService::canControl)
                .map(app.charka.model.Character::getId).collect(java.util.stream.Collectors.toSet()));
    }

    @PostMapping("/participants/{participantId}/move")
    public ResponseEntity<Void> move(@PathVariable Long campaignId, @PathVariable Long participantId,
                                     @RequestParam int x, @RequestParam int y, @RequestParam(defaultValue = "false") boolean free,
                                     @RequestParam(required = false) Integer level, HttpServletRequest request) {
        boolean gm = accessService.isGm(campaignService.getById(campaignId));
        // без ограничения бега ставит только мастер, и только в режиме «Бог» (A-15)
        if (free && !gm) {
            throw new IllegalArgumentException("Без ограничения бега ставит только мастер");
        }
        if (gm && GmMode.npcOnly(request)) {
            if (free) {
                throw new IllegalArgumentException("В режиме «За NPC» — только по бегу; без ограничения — в режиме «Бог»");
            }
            if (!battleFieldService.isNpc(campaignId, participantId)) {
                throw new IllegalArgumentException("В режиме «За NPC» фишки игроков не двигаются");
            }
        }
        battleFieldService.move(campaignId, participantId, x, y, free, level);
        return ResponseEntity.noContent().build();
    }

    /** M-22: по лестнице на соседний уровень; {@code level} — куда, если лестница ведёт и вверх, и вниз */
    @PostMapping(value = "/participants/{participantId}/climb", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> climb(@PathVariable Long campaignId, @PathVariable Long participantId,
                                        @RequestParam(required = false) Integer level, @RequestParam(defaultValue = "false") boolean free,
                                        HttpServletRequest request) {
        boolean gm = accessService.isGm(campaignService.getById(campaignId));
        if (free && (!gm || GmMode.npcOnly(request))) {
            throw new IllegalArgumentException("Без ограничения бега — мастер в режиме «Бог»");
        }
        if (gm && GmMode.npcOnly(request) && !battleFieldService.isNpc(campaignId, participantId)) {
            throw new IllegalArgumentException("В режиме «За NPC» фишки игроков не двигаются");
        }
        return ResponseEntity.ok(battleFieldService.climb(campaignId, participantId, level, free));
    }

    /** «＋ Фишка» мастера: {@code character} — кто из кампании или {@code template} — новый NPC; клетка {@code x}, {@code y} */
    @PostMapping("/tokens")
    public ResponseEntity<String> addToken(@PathVariable Long campaignId, @RequestParam(required = false) Long character,
                                           @RequestParam(required = false) String template, @RequestParam int x, @RequestParam int y,
                                           @RequestParam(defaultValue = "0") int level, HttpServletRequest request) {
        godOnly(request, "＋ Фишка");
        return ResponseEntity.ok(battleFieldService.addToken(campaignId, character, template, x, y, level));
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

    /** Поле видимости участника и что с каждой другой фишкой */
    @GetMapping("/participants/{participantId}/view")
    public BattleFieldService.View view(@PathVariable Long campaignId, @PathVariable Long participantId) {
        return battleFieldService.view(campaignId, participantId, viewer(campaignId));
    }

    @GetMapping("/sight")
    public BattleFieldService.Sight sight(@PathVariable Long campaignId, @RequestParam Long from, @RequestParam Long to) {
        return battleFieldService.sight(campaignId, from, to, viewer(campaignId));
    }

    @PostMapping("/door")
    public ResponseEntity<Void> door(@PathVariable Long campaignId, @RequestParam String edge, @RequestParam(defaultValue = "0") int level) {
        battleFieldService.toggleDoor(campaignId, edge, level);
        return ResponseEntity.noContent().build();
    }

    /** M-23: выбить дверь, разбить окно; стену — мастер */
    @PostMapping(value = "/break", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> breakEdge(@PathVariable Long campaignId, @RequestParam String edge,
                                            @RequestParam(defaultValue = "0") int level, HttpServletRequest request) {
        boolean gm = accessService.isGm(campaignService.getById(campaignId));
        return ResponseEntity.ok(battleFieldService.breakEdge(campaignId, edge, gm, level));
    }

    /** M-23: попали в бочку или баллон — взрыв; {@code by} — кто стрелял, пусто — мастер */
    @PostMapping(value = "/detonate", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> detonate(@PathVariable Long campaignId, @RequestParam int x, @RequestParam int y,
                                           @RequestParam(defaultValue = "0") int level, @RequestParam(required = false) Long by) {
        if (by == null && !accessService.isGm(campaignService.getById(campaignId))) {
            throw new IllegalArgumentException("Выберите, кто стрелял: «Я —» или своя фишка");
        }
        if (by != null) {
            app.charka.model.Character shooter = campaignService.getById(campaignId).getCharacters().stream()
                    .filter(character -> character.getId().equals(by)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Нет такого персонажа"));
            if (!accessService.canControl(shooter)) {
                throw new IllegalArgumentException("Стреляет свой персонаж");
            }
        }
        app.charka.attack.AttackResult result = attackService.detonate(campaignId, level, x, y, by);
        return ResponseEntity.ok(result.title() + " · " + result.outcome());
    }

    @PostMapping("/pointer")
    public ResponseEntity<Void> pointer(@PathVariable Long campaignId, @RequestParam int x, @RequestParam int y,
                                        @RequestParam(required = false) Integer fromX, @RequestParam(required = false) Integer fromY,
                                        @RequestParam(required = false) String who) {
        battleFieldService.point(campaignId, x, y, fromX, fromY, who);
        return ResponseEntity.noContent().build();
    }

    /** Копия карты боя для редактора (M-11) */
    @GetMapping("/doc")
    public app.charka.map.EditableMap doc(@PathVariable Long campaignId) {
        return battleFieldService.editable(campaignId);
    }

    /** Мастер правит копию карты боя: документ целиком */
    @PostMapping(value = "/doc", consumes = "application/json")
    public ResponseEntity<Void> doc(@PathVariable Long campaignId, @RequestBody String json, HttpServletRequest request) {
        godOnly(request, "Правка карты");
        battleFieldService.updateDoc(campaignId, json);
        return ResponseEntity.noContent().build();
    }

    /** A-15: инструменты, которых нет в режиме «За NPC» (маршруты только для мастера — {@code AccessInterceptor}) */
    private static void godOnly(HttpServletRequest request, String what) {
        if (GmMode.npcOnly(request)) {
            throw new IllegalArgumentException(what + " — в режиме «Бог»");
        }
    }
}
