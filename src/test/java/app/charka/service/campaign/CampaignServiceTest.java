package app.charka.service.campaign;

import app.charka.model.Campaign;
import app.charka.repository.CampaignRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CampaignService — создание")
class CampaignServiceTest {

    @Mock private CampaignRepository campaignRepository;

    private CampaignService campaignService;

    @BeforeEach
    void setUp() {
        campaignService = new CampaignService(campaignRepository,
                new GameSystemRegistry(List.of(new GenericSystem(), new Cp2020System(null, null))));
    }

    @Test
    @DisplayName("кампания CP2020: текущая дата = дата начала")
    void createsCp2020() {
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
        Campaign campaign = new Campaign();
        campaign.setName("Последний шанс");
        campaign.setSystem(Cp2020System.CODE);
        campaign.setStartDate(LocalDate.of(2037, 10, 27));

        Campaign saved = campaignService.create(campaign);

        assertThat(saved.getSystem()).isEqualTo("CP2020");
        assertThat(saved.getCurrentDate()).isEqualTo(LocalDate.of(2037, 10, 27));
    }

    @Test
    @DisplayName("без даты начала — сегодня")
    void defaultsStartDate() {
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
        Campaign campaign = new Campaign();
        campaign.setSystem(GenericSystem.CODE);

        Campaign saved = campaignService.create(campaign);

        assertThat(saved.getStartDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getCurrentDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("неизвестная система — ошибка, ничего не сохраняется")
    void rejectsUnknownSystem() {
        Campaign campaign = new Campaign();
        campaign.setSystem("NOPE");

        assertThatThrownBy(() -> campaignService.create(campaign)).isInstanceOf(IllegalArgumentException.class);
        verify(campaignRepository, never()).save(any());
    }
}
