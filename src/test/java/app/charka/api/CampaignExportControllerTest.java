package app.charka.api;

import app.charka.GlobalExceptionHandler;
import app.charka.model.Campaign;
import app.charka.service.campaign.CampaignExportService;
import app.charka.service.campaign.CampaignService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CampaignExportController – unit-тесты через Standalone MockMvc")
class CampaignExportControllerTest {

    // ======== DEPENDENCIES ========
    @Mock
    private CampaignService campaignService;
    @Mock
    private CampaignExportService exportService;
    @InjectMocks
    private CampaignExportController controller;

    // ======== INFRASTRUCTURE ========
    private MockMvc mockMvc;

    // ======== CONSTANTS ========
    private static final long VALID_ID = 42L;
    private static final long MISSING_ID = 99L;
    private static final long ERROR_ID = 7L;
    private static final String FILE_NAME = "campaign-42.json";
    private static final String FILE_JSON = "{\"id\":42}";
    private static final String NOT_FOUND = "Campaign not found";
    private static final String ERROR_MSG = "JSON error";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ======================================================================
    //                               HAPPY PATH
    // ======================================================================
    @Test
    @DisplayName("200 OK – успешный экспорт кампании")
    void downloadCampaign_returnsFileWhenCampaignExists() throws Exception {
        Campaign campaign = new Campaign();
        campaign.setId(VALID_ID);

        when(campaignService.getById(VALID_ID)).thenReturn(campaign);
        when(exportService.generateFileName(campaign)).thenReturn(FILE_NAME);
        when(exportService.exportCampaignToJson(campaign)).thenReturn(FILE_JSON);

        mockMvc.perform(get("/api/campaigns/{id}/download", VALID_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + FILE_NAME + "\"; filename*=UTF-8''campaign-42.json"
                ))
                .andExpect(content().string(FILE_JSON));

        verify(campaignService).getById(VALID_ID);
        verify(exportService).generateFileName(campaign);
        verify(exportService).exportCampaignToJson(campaign);
        verifyNoMoreInteractions(campaignService, exportService);
    }

    // ======================================================================
    //                               NOT FOUND
    // ======================================================================
    @Test
    @DisplayName("404 Not Found – кампания отсутствует")
    void downloadCampaign_returns404WhenCampaignAbsent() throws Exception {
        when(campaignService.getById(MISSING_ID))
                .thenThrow(new IllegalArgumentException("Campaign not found for ID: " + MISSING_ID));

        mockMvc.perform(get("/api/campaigns/{id}/download", MISSING_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(NOT_FOUND));

        verify(campaignService).getById(MISSING_ID);
        verifyNoInteractions(exportService);
    }

    // ======================================================================
    //                               EXPORT ERROR
    // ======================================================================
    @Test
    @DisplayName("500 Internal Server Error – ошибка генерации JSON")
    void downloadCampaign_returns500WhenExportFails() throws Exception {
        Campaign campaign = new Campaign();
        campaign.setId(ERROR_ID);

        when(campaignService.getById(ERROR_ID)).thenReturn(campaign);
        when(exportService.generateFileName(campaign)).thenReturn("campaign-7.json");
        when(exportService.exportCampaignToJson(campaign))
                .thenThrow(new RuntimeException(ERROR_MSG));

        mockMvc.perform(get("/api/campaigns/{id}/download", ERROR_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Error when generating JSON for campaign id=7: " + ERROR_MSG));

        verify(campaignService).getById(ERROR_ID);
        verify(exportService).generateFileName(campaign);
        verify(exportService).exportCampaignToJson(campaign);
        verifyNoMoreInteractions(campaignService, exportService);
    }
}
