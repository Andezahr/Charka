package app.charka.portrait;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneOffset;

/**
 * PH-20: портрет персонажа. Загружает владелец листа (игрок — своему, мастер — любому: {@code AccessInterceptor}
 * по адресу персонажа); смотрят все за столом по адресу кампании, NPC в тумане — нет.
 */
@Controller
@RequiredArgsConstructor
public class PortraitController {

    private final PortraitService portraitService;

    @PostMapping("/character/{characterId}/portrait")
    public String upload(@PathVariable Long characterId, @RequestParam("file") MultipartFile file,
                         RedirectAttributes redirectAttributes) {
        try {
            portraitService.set(characterId, file.getBytes(), file.getContentType());
            redirectAttributes.addFlashAttribute("successMessage", "Портрет обновлён");
        } catch (IllegalArgumentException | IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/character/" + characterId;
    }

    @PostMapping("/character/{characterId}/portrait/delete")
    public String remove(@PathVariable Long characterId, RedirectAttributes redirectAttributes) {
        portraitService.remove(characterId);
        redirectAttributes.addFlashAttribute("successMessage", "Портрет убран");
        return "redirect:/character/" + characterId;
    }

    /** Картинка; адрес с версией, поэтому кэш на год, ETag — на случай, если браузер всё же спросит */
    @GetMapping("/campaign/{campaignId}/portraits/{characterId}")
    public ResponseEntity<byte[]> show(@PathVariable Long campaignId, @PathVariable Long characterId) {
        return portraitService.forViewer(campaignId, characterId)
                .map(portrait -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(portrait.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                        .eTag("\"" + portrait.getUpdatedAt().toInstant(ZoneOffset.UTC).toEpochMilli() + "\"")
                        .body(portrait.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
