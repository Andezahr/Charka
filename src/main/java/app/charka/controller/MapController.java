package app.charka.controller;

import app.charka.map.BattleMapService;
import app.charka.map.MapDoc;
import app.charka.model.BattleMap;
import app.charka.service.campaign.CampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Карты боя кампании (M-06): список, создать, копировать, удалить; редактор.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/campaign/{campaignId}/maps")
public class MapController {

    private final BattleMapService battleMapService;
    private final CampaignService campaignService;
    private final app.charka.map.MapGenerator mapGenerator;

    @GetMapping
    public String list(@PathVariable Long campaignId, Model model) {
        model.addAttribute("campaign", campaignService.getById(campaignId));
        Map<Long, MapDoc> docs = new LinkedHashMap<>();
        battleMapService.list(campaignId).forEach(map -> docs.put(map.getId(), MapDoc.parse(map.getDoc())));
        model.addAttribute("maps", battleMapService.list(campaignId));
        model.addAttribute("docs", docs);
        model.addAttribute("defaultWidth", BattleMapService.DEFAULT_WIDTH);
        model.addAttribute("defaultHeight", BattleMapService.DEFAULT_HEIGHT);
        model.addAttribute("templates", mapGenerator.templates());
        return "maps";
    }

    @PostMapping
    public String create(@PathVariable Long campaignId, @RequestParam String name,
                         @RequestParam(defaultValue = "30") int width, @RequestParam(defaultValue = "20") int height,
                         RedirectAttributes redirectAttributes) {
        try {
            BattleMap map = battleMapService.create(campaignId, name, width, height);
            return "redirect:/campaign/" + campaignId + "/maps/" + map.getId() + "/edit";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mapError", e.getMessage());
            return "redirect:/campaign/" + campaignId + "/maps";
        }
    }

    /** Редактор: рисует app.js, документ приходит и сохраняется через {@code MapEditorController} */
    @GetMapping("/{mapId}/edit")
    public String edit(@PathVariable Long campaignId, @PathVariable Long mapId, Model model) {
        model.addAttribute("campaign", campaignService.getById(campaignId));
        BattleMap map = battleMapService.get(campaignId, mapId);
        model.addAttribute("map", map);
        model.addAttribute("hasBackground", map.getBackground() != null);
        // M-21: проверка на играбельность по сохранённой версии; после правок — перезагрузить страницу
        model.addAttribute("audit", app.charka.map.MapAudit.of(MapDoc.parse(map.getDoc()), battleMapService.rules(campaignId)));
        model.addAttribute("docUrl", "/campaign/" + campaignId + "/maps/" + mapId + "/doc");
        model.addAttribute("backUrl", "/campaign/" + campaignId + "/maps");
        model.addAttribute("inFight", false);
        model.addAttribute("templates", mapGenerator.templates());
        return "map-editor";
    }

    /**
     * Новая карта генератором; размер — шаблона, если не задан.
     */
    @PostMapping("/generate")
    public String generate(@PathVariable Long campaignId, @RequestParam String template,
                           @RequestParam(required = false) Integer width, @RequestParam(required = false) Integer height,
                           @RequestParam(required = false) Long seed, RedirectAttributes redirectAttributes) {
        try {
            app.charka.map.MapGenerator.Template chosen = mapGenerator.templateOf(template);
            MapDoc doc = mapGenerator.generate(template, width == null ? chosen.width() : width, height == null ? chosen.height() : height,
                    seed == null ? new java.util.Random().nextLong() : seed, battleMapService.rules(campaignId));
            BattleMap map = battleMapService.save(campaignId, chosen.label(), doc);
            return "redirect:/campaign/" + campaignId + "/maps/" + map.getId() + "/edit";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mapError", e.getMessage());
            return "redirect:/campaign/" + campaignId + "/maps";
        }
    }

    /**
     * M-20: карта из текста {@link app.charka.map.MapText} (файл или вставка) или из JSON документа карты.
     * Название — из поля, иначе из заголовка, иначе имя файла.
     */
    @PostMapping("/import")
    public String importMap(@PathVariable Long campaignId,
                            @RequestParam(value = "file", required = false) org.springframework.web.multipart.MultipartFile file,
                            @RequestParam(required = false) String text, @RequestParam(required = false) String name,
                            RedirectAttributes redirectAttributes) {
        try {
            String source = text;
            String fileName = null;
            if (file != null && !file.isEmpty()) {
                source = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
                fileName = file.getOriginalFilename();
            }
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("Выберите файл карты или вставьте её текст");
            }
            MapDoc doc;
            String title = null;
            java.util.List<String> warnings = java.util.List.of();
            if (source.strip().startsWith("{")) {
                doc = MapDoc.parse(source);
            } else {
                app.charka.map.MapText.Parsed parsed = app.charka.map.MapText.parse(source, battleMapService.rules(campaignId));
                doc = parsed.doc();
                title = parsed.name();
                warnings = parsed.warnings();
            }
            if (name != null && !name.isBlank()) {
                title = name;
            }
            if (title == null && fileName != null) {
                title = fileName.replaceFirst("(\\.map)?\\.(txt|json)$", "");
            }
            BattleMap map = battleMapService.save(campaignId, title == null || title.isBlank() ? "Карта" : title, doc);
            if (!warnings.isEmpty()) {
                redirectAttributes.addFlashAttribute("mapNotice", "Карта загружена, но:\n" + String.join("\n", warnings));
            }
            return "redirect:/campaign/" + campaignId + "/maps/" + map.getId() + "/edit";
        } catch (IllegalArgumentException | java.io.IOException e) {
            redirectAttributes.addFlashAttribute("mapError", e.getMessage());
            redirectAttributes.addFlashAttribute("importText", text);
            return "redirect:/campaign/" + campaignId + "/maps";
        }
    }

    /** M-20: карта текстом {@link app.charka.map.MapText} — скачать, поправить, загрузить обратно */
    @GetMapping("/{mapId}/text")
    public org.springframework.http.ResponseEntity<String> text(@PathVariable Long campaignId, @PathVariable Long mapId) {
        BattleMap map = battleMapService.get(campaignId, mapId);
        String body = app.charka.map.MapText.write(map.getName(), MapDoc.parse(map.getDoc()), battleMapService.rules(campaignId));
        String file = map.getName().replaceAll("[\\\\/:*?\"<>|]", "_") + app.charka.map.MapText.EXTENSION;
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition
                        .attachment().filename(file, java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .contentType(org.springframework.http.MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body(body);
    }

    /** «🎲 Ещё вариант»: тот же размер, новое зерно; прежний вариант заменяется */
    @PostMapping("/{mapId}/regenerate")
    public String regenerate(@PathVariable Long campaignId, @PathVariable Long mapId, @RequestParam String template,
                             RedirectAttributes redirectAttributes) {
        try {
            MapDoc old = MapDoc.parse(battleMapService.get(campaignId, mapId).getDoc());
            MapDoc doc = mapGenerator.generate(template, old.w(), old.h(), new java.util.Random().nextLong(), battleMapService.rules(campaignId));
            battleMapService.updateDoc(campaignId, mapId, doc.toJson());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mapError", e.getMessage());
        }
        return "redirect:/campaign/" + campaignId + "/maps/" + mapId + "/edit";
    }

    /** M-11: подложка карты */
    @PostMapping("/{mapId}/background")
    public String uploadBackground(@PathVariable Long campaignId, @PathVariable Long mapId,
                                   @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                   RedirectAttributes redirectAttributes) {
        try {
            battleMapService.setBackground(campaignId, mapId, file.getBytes(), file.getContentType());
        } catch (IllegalArgumentException | java.io.IOException e) {
            redirectAttributes.addFlashAttribute("mapError", e.getMessage());
        }
        return "redirect:/campaign/" + campaignId + "/maps/" + mapId + "/edit";
    }

    @PostMapping("/{mapId}/background/delete")
    public String removeBackground(@PathVariable Long campaignId, @PathVariable Long mapId) {
        battleMapService.removeBackground(campaignId, mapId);
        return "redirect:/campaign/" + campaignId + "/maps/" + mapId + "/edit";
    }

    /** Картинка подложки; адрес с версией, поэтому кэшируется надолго */
    @GetMapping("/{mapId}/background")
    public org.springframework.http.ResponseEntity<byte[]> background(@PathVariable Long campaignId, @PathVariable Long mapId) {
        BattleMap map = battleMapService.get(campaignId, mapId);
        if (map.getBackground() == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(map.getBackgroundType()))
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(30)))
                .body(map.getBackground());
    }

    @PostMapping("/{mapId}/copy")
    public String copy(@PathVariable Long campaignId, @PathVariable Long mapId) {
        battleMapService.copy(campaignId, mapId);
        return "redirect:/campaign/" + campaignId + "/maps";
    }

    @PostMapping("/{mapId}/rename")
    public String rename(@PathVariable Long campaignId, @PathVariable Long mapId, @RequestParam String name,
                         RedirectAttributes redirectAttributes) {
        try {
            battleMapService.rename(campaignId, mapId, name);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mapError", e.getMessage());
        }
        return "redirect:/campaign/" + campaignId + "/maps";
    }

    @PostMapping("/{mapId}/delete")
    public String delete(@PathVariable Long campaignId, @PathVariable Long mapId) {
        battleMapService.delete(campaignId, mapId);
        return "redirect:/campaign/" + campaignId + "/maps";
    }
}
