package app.charka.service.campaign;

import app.charka.model.Campaign;
import app.charka.service.ChronicleService;
import app.charka.service.counter.HealthCounterService;
import app.charka.system.GameSystemRegistry;
import app.charka.system.GenericSystem;
import app.charka.system.cp2020.Cp2020System;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CampaignTimelineService — промотка времени")
class CampaignTimelineServiceTest {

    private static final LocalDate START = LocalDate.of(2037, 10, 27);

    @Mock private ChronicleService chronicleService;
    @Mock private CampaignService campaignService;
    @Mock private HealthCounterService healthCounterService;

    private CampaignTimelineService timelineService;

    @BeforeEach
    void setUp() {
        GameSystemRegistry registry = new GameSystemRegistry(
                List.of(new GenericSystem(), new Cp2020System(healthCounterService, null)));
        timelineService = new CampaignTimelineService(chronicleService, campaignService, registry);
    }

    private void givenCampaign(String system) {
        Campaign campaign = new Campaign();
        campaign.setId(1L);
        campaign.setSystem(system);
        campaign.setCurrentDate(START);
        when(campaignService.getById(1L)).thenReturn(campaign);
    }

    @Test
    @DisplayName("CP2020 — сдвигает дату и лечит по правилам CP2020")
    void cp2020Heals() {
        givenCampaign(Cp2020System.CODE);

        timelineService.proceedDate(1L, 3, "Отлежались");

        verify(campaignService).updateCurrentDate(1L, START.plusDays(3));
        verify(healthCounterService).healOverTime(1L, 3);
    }

    @Test
    @DisplayName("GENERIC — дата сдвигается, лечения CP2020 нет")
    void genericDoesNotHeal() {
        givenCampaign(GenericSystem.CODE);

        timelineService.proceedDate(1L, 3, "Прошло время");

        verify(campaignService).updateCurrentDate(1L, START.plusDays(3));
        verify(healthCounterService, never()).healOverTime(anyLong(), anyInt());
    }
}
