package app.Charka.api;

import app.Charka.model.Campaign;
import app.Charka.repository.CampaignRepository;
import app.Charka.service.CampaignExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignExportController {
    private final CampaignRepository campaignRepository;
    private final CampaignExportService exportService;

    public CampaignExportController(CampaignRepository campaignRepository,
                                    CampaignExportService exportService) {
        this.campaignRepository = campaignRepository;
        this.exportService = exportService;
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<ByteArrayResource> downloadCampaign(@PathVariable Long id) {
        try {
            Campaign campaign = campaignRepository.findById(id).orElseThrow();
            String jsonContent = exportService.exportCampaignToJson(campaign);
            byte[] jsonBytes = jsonContent.getBytes(StandardCharsets.UTF_8);
            ByteArrayResource resource = new ByteArrayResource(jsonBytes);
            String fileName = exportService.generateFileName(campaign);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=" + fileName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(jsonBytes.length)
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
