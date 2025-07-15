package app.charka.api;

import app.charka.model.Campaign;
import app.charka.repository.CampaignRepository;
import app.charka.service.CampaignExportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignExportController {
    private static final Logger logger = LoggerFactory.getLogger(CampaignExportController.class);
    private final CampaignRepository campaignRepository;
    private final CampaignExportService exportService;

    public CampaignExportController(CampaignRepository campaignRepository,
                                    CampaignExportService exportService) {
        this.campaignRepository = campaignRepository;
        this.exportService = exportService;
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<ByteArrayResource> downloadCampaign(@PathVariable Long id) {
        logger.info("Получен запрос на экспорт кампании id={}", id);
        Campaign campaign = campaignRepository.findById(id)
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
            logger.error("Ошибка при экспорте кампании id={}", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка генерации JSON: " + e.getMessage());
        }
    }
}
