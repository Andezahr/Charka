package app.charka.service.campaign;

import app.charka.model.Campaign;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class    CampaignExportService {

    private final ObjectMapper objectMapper;

    public CampaignExportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String exportCampaignToJson(Campaign campaign) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(campaign);
    }

    public String generateFileName(Campaign campaign) {
        return "campaign_" + campaign.getId() + "_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) +
                ".json";
    }
}
