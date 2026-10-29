package app.charka.service.campaign;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.counters.Counter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CampaignExportService – сериализация кампании в JSON")
class CampaignExportServiceTest {

    private final CampaignExportService exportService =
            new CampaignExportService(JsonMapper.builder().findAndAddModules().build());

    @Test
    @DisplayName("Экспорт персонажа со счётчиком не уходит в бесконечную рекурсию")
    void exportCampaign_withCounters() throws Exception {
        Campaign campaign = new Campaign();
        campaign.setId(1L);
        campaign.setName("Последний шанс");
        campaign.setStartDate(LocalDate.of(2037, 10, 27));

        Character character = new Character();
        character.setId(1L);
        character.setName("Колясик");
        character.setCampaign(campaign);

        Counter counter = new Counter();
        counter.setId(1L);
        counter.setName("HP");
        counter.setValue(10);
        counter.setType("SIMPLE_INTEGER");
        counter.setCharacter(character);

        character.setCounters(List.of(counter));
        campaign.setCharacters(List.of(character));

        String json = exportService.exportCampaignToJson(campaign);

        assertTrue(json.contains("\"name\" : \"HP\""));
    }
}
