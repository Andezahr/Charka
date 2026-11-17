package app.charka.service.campaign;

import app.charka.model.Campaign;
import app.charka.model.CharacterPortrait;
import app.charka.repository.CharacterPortraitRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class CampaignExportService {

    /** PH-20: портрет персонажа в экспорте — {@code {"type": "image/webp", "data": "<base64>"}} */
    public static final String PORTRAIT = "portrait";

    private final ObjectMapper objectMapper;
    private final CharacterPortraitRepository portraitRepository;

    @Autowired
    public CampaignExportService(ObjectMapper objectMapper, CharacterPortraitRepository portraitRepository) {
        this.objectMapper = objectMapper;
        this.portraitRepository = portraitRepository;
    }

    /** Без портретов (тесты сериализации) */
    public CampaignExportService(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    public String exportCampaignToJson(Campaign campaign) throws JsonProcessingException {
        JsonNode tree = objectMapper.valueToTree(campaign);
        JsonNode characters = tree.get("characters");
        if (portraitRepository != null && characters != null && characters.isArray()) {
            for (int index = 0; index < characters.size() && index < campaign.getCharacters().size(); index++) {
                Long id = campaign.getCharacters().get(index).getId();
                Optional<CharacterPortrait> portrait = id == null ? Optional.empty() : portraitRepository.findById(id);
                if (portrait.isPresent()) {
                    ((ObjectNode) characters.get(index)).putObject(PORTRAIT)
                            .put("type", portrait.get().getContentType())
                            .put("data", portrait.get().getData());
                }
            }
        }
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
    }

    public String generateFileName(Campaign campaign) {
        return "campaign_" + campaign.getId() + "_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) +
                ".json";
    }
}
