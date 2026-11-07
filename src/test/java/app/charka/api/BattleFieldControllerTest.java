package app.charka.api;

import app.charka.exception.ApiExceptionHandler;
import app.charka.map.BattleFieldService;
import app.charka.model.Campaign;
import app.charka.security.AccessService;
import app.charka.security.GmMode;
import app.charka.service.campaign.CampaignService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("BattleFieldController — режимы мастера «Бог» и «За NPC» (A-15)")
class BattleFieldControllerTest {

    private static final long CAMPAIGN = 1L;
    private static final long PLAYER_TOKEN = 10L;
    private static final long NPC_TOKEN = 20L;
    private static final Cookie NPC_MODE = new Cookie(GmMode.COOKIE, GmMode.NPC);
    private static final Cookie GOD_MODE = new Cookie(GmMode.COOKIE, "god");

    @Mock
    private BattleFieldService battleFieldService;
    @Mock
    private AccessService accessService;
    @Mock
    private CampaignService campaignService;
    @InjectMocks
    private BattleFieldController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
        Campaign campaign = new Campaign();
        lenient().when(campaignService.getById(CAMPAIGN)).thenReturn(campaign);
        lenient().when(accessService.isGm(campaign)).thenReturn(true);
        lenient().when(battleFieldService.isNpc(CAMPAIGN, PLAYER_TOKEN)).thenReturn(false);
        lenient().when(battleFieldService.isNpc(CAMPAIGN, NPC_TOKEN)).thenReturn(true);
    }

    private static String moveUrl(long token) {
        return "/campaign/" + CAMPAIGN + "/encounter/map/participants/" + token + "/move";
    }

    @Nested
    @DisplayName("«За NPC»")
    class NpcMode {

        @Test
        @DisplayName("фишку игрока не сдвинуть запросом")
        void playerTokenRefused() throws Exception {
            mockMvc.perform(post(moveUrl(PLAYER_TOKEN)).param("x", "3").param("y", "4").cookie(NPC_MODE))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("В режиме «За NPC» фишки игроков не двигаются"));
            verify(battleFieldService, never()).move(anyLong(), anyLong(), anyInt(), anyInt(), anyBoolean(), any());
        }

        @Test
        @DisplayName("NPC ходит по бегу")
        void npcMovesByRun() throws Exception {
            mockMvc.perform(post(moveUrl(NPC_TOKEN)).param("x", "3").param("y", "4").cookie(NPC_MODE))
                    .andExpect(status().isNoContent());
            verify(battleFieldService).move(CAMPAIGN, NPC_TOKEN, 3, 4, false, null);
        }

        @Test
        @DisplayName("без лимита бега нельзя даже NPC")
        void freeRefused() throws Exception {
            mockMvc.perform(post(moveUrl(NPC_TOKEN)).param("x", "3").param("y", "4").param("free", "true").cookie(NPC_MODE))
                    .andExpect(status().isBadRequest());
            verify(battleFieldService, never()).move(anyLong(), anyLong(), anyInt(), anyInt(), anyBoolean(), any());
        }

        @Test
        @DisplayName("＋ Фишка и правка карты — отказ")
        void godToolsRefused() throws Exception {
            mockMvc.perform(post("/campaign/" + CAMPAIGN + "/encounter/map/tokens").param("x", "1").param("y", "1").cookie(NPC_MODE))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/campaign/" + CAMPAIGN + "/encounter/map/doc").contentType(MediaType.APPLICATION_JSON)
                            .content("{}").cookie(NPC_MODE))
                    .andExpect(status().isBadRequest());
            verify(battleFieldService, never()).addToken(any(), any(), any(), anyInt(), anyInt());
            verify(battleFieldService, never()).updateDoc(anyLong(), anyString());
        }
    }

    @Nested
    @DisplayName("«Бог» — как раньше")
    class GodMode {

        @Test
        @DisplayName("фишка игрока без лимита бега")
        void playerTokenFree() throws Exception {
            mockMvc.perform(post(moveUrl(PLAYER_TOKEN)).param("x", "3").param("y", "4").param("free", "true").cookie(GOD_MODE))
                    .andExpect(status().isNoContent());
            verify(battleFieldService).move(CAMPAIGN, PLAYER_TOKEN, 3, 4, true, null);
        }

        @Test
        @DisplayName("без cookie — тоже «Бог»")
        void defaultIsGod() throws Exception {
            mockMvc.perform(post(moveUrl(PLAYER_TOKEN)).param("x", "3").param("y", "4"))
                    .andExpect(status().isNoContent());
            verify(battleFieldService).move(CAMPAIGN, PLAYER_TOKEN, 3, 4, false, null);
        }
    }

    @Nested
    @DisplayName("Игрок")
    class Player {

        @Test
        @DisplayName("cookie режима ему ничего не меняет: свою фишку двигает по бегу")
        void cookieIgnored() throws Exception {
            when(accessService.isGm(any())).thenReturn(false);
            mockMvc.perform(post(moveUrl(PLAYER_TOKEN)).param("x", "3").param("y", "4").cookie(NPC_MODE))
                    .andExpect(status().isNoContent());
            verify(battleFieldService).move(CAMPAIGN, PLAYER_TOKEN, 3, 4, false, null);
        }
    }
}
