package app.charka.api;

import app.charka.model.Campaign;
import app.charka.service.campaign.CampaignExportService;
import app.charka.service.campaign.CampaignService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;


/**
 * Контроллер экспорта всей информации о кампании в JSON-файл
 */
@RestController
@RequestMapping("/api/campaigns")
public class CampaignExportController {
    private static final Logger logger = LoggerFactory.getLogger(CampaignExportController.class);

    private final CampaignService campaignService;
    private final CampaignExportService exportService;

    public CampaignExportController(CampaignService campaignService,
                                    CampaignExportService exportService) {
        this.campaignService = campaignService;
        this.exportService = exportService;
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<ByteArrayResource> downloadCampaign(@PathVariable Long id) {
        logger.info("Получен запрос на экспорт кампании id={}", id);

        Campaign campaign = campaignService.getById(id)
                .orElseThrow(() -> {
                    logger.warn("Кампания с id={} не найдена", id);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Кампания не найдена");
                });

        String fileName = exportService.generateFileName(campaign);
        logger.debug("Имя файла для экспорта: {}", fileName);

        try {
            String jsonContent = exportService.exportCampaignToJson(campaign);
            logger.trace("Содержимое JSON (первые 500 символов): {}",
                    jsonContent.length() > 500 ? jsonContent.substring(0, 500) : jsonContent);

            byte[] jsonBytes = jsonContent.getBytes(StandardCharsets.UTF_8);
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            logger.debug("Размер экспортируемого файла: {} байт", jsonBytes.length);

            ByteArrayResource resource = new ByteArrayResource(jsonBytes);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encodedFileName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(jsonBytes.length)
                    .body(resource);

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Ошибка генерации JSON для кампании id=" + id + ": " + e.getMessage(),
                    e
            );
        }
    }
}
