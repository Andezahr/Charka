package app.charka.security;

import app.charka.encounter.EncounterService;
import app.charka.encounter.RoundActions;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.EncounterParticipant;
import app.charka.repository.AppUserRepository;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.support.CharkaSecuredTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * TURN-01: обход всех изменяющих маршрутов персонажа и боя от имени игрока не на его ходу.
 */
@CharkaSecuredTest
@Transactional
@DisplayName("Ход жёстко: вне своего хода игрок меняет только разрешённое (TURN-01)")
class TurnGuardTest {

    /** Что игрок делает вне хода — то же, что в TurnGuard; новый пункт сюда — только вместе с ним */
    private static final Set<String> ALLOWED = Set.of(
            "/character/{}/counters/{}/health/damage",
            "/character/{}/counters/{}/health/save/{}",
            "/campaign/{}/encounter/participants/{}/damage",
            "/campaign/{}/encounter/participants/{}/save/{}",
            "/campaign/{}/encounter/participants/{}/declare/{}",
            "/campaign/{}/encounter/participants/{}/join",
            "/campaign/{}/encounter/map/pointer",
            "/campaign/{}/encounter/next");
    // фишка из лотка ставится вне хода (BF-02); фишка на карте — нет, поэтому ход фишкой в этом списке нет
    /** Записи игрока, бою не мешают */
    private static final Pattern ALLOWED_PREFIX = Pattern.compile("^/character/\\{}/(notes|categories|contacts|profile|skills/pin)(/.*)?$");
    /** Мастерские маршруты боя: игроку закрыты раньше (AccessInterceptor, 404) */
    private static final Pattern VARIABLE = Pattern.compile("\\{}");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private EncounterService encounterService;
    @Autowired private EntityManager entityManager;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;

    private Campaign campaign;
    private Character mine;
    private Long minePid;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        Campaign created = new Campaign();
        created.setName("Ночной город");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        mine = member("Колясик", false);
        Character bandit = member("Бандит", true);
        campaign.setGm(userService.register("master", "secret1"));
        campaign = campaignRepository.save(campaign);
        mine.setPlayer(userService.register("kolya", "secret2"));
        characterRepository.save(mine);
        encounterService.start(campaign.getId(), List.of(mine.getId(), bandit.getId()));
        List<EncounterParticipant> rows = encounterService.view(campaign.getId()).orElseThrow().rows().stream()
                .map(app.charka.encounter.EncounterView.Row::participant).toList();
        for (EncounterParticipant participant : rows) {
            boolean isMine = participant.getCharacter().getId().equals(mine.getId());
            encounterService.setInitiative(campaign.getId(), participant.getId(), isMine ? 5 : 30);
            if (isMine) {
                minePid = participant.getId();
            }
        }
        entityManager.flush();
        entityManager.clear();
        assertThat(encounterService.currentCharacterId(campaign.getId())).isEqualTo(bandit.getId());
    }

    private Character member(String name, boolean npc) {
        Character character = new Character();
        character.setName(name);
        character.setNpc(npc);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    /** Изменяющие маршруты персонажа и боя: шаблон → метод */
    private List<String[]> routes() {
        List<String[]> routes = new ArrayList<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String raw : info.getPatternValues()) {
                String pattern = raw.replaceAll("\\{[^}]+}", "{}");
                boolean ours = pattern.startsWith("/character/{}") || pattern.startsWith("/api/characters/{}")
                        || pattern.startsWith("/campaign/{}/encounter");
                if (!ours) {
                    continue;
                }
                Set<String> verbs = new TreeSet<>();
                if (methods.isEmpty()) {
                    verbs.add("POST");
                }
                methods.stream().map(Enum::name).filter(name -> !name.equals("GET")).forEach(verbs::add);
                verbs.forEach(verb -> routes.add(new String[]{pattern, verb}));
            }
        }
        return routes;
    }

    private String url(String pattern) {
        Matcher matcher = VARIABLE.matcher(pattern);
        StringBuilder url = new StringBuilder();
        boolean first = true;
        while (matcher.find()) {
            String value;
            if (first) {
                value = String.valueOf(pattern.startsWith("/campaign/") ? campaign.getId() : mine.getId());
            } else if (pattern.substring(0, matcher.start()).endsWith("/participants/")) {
                value = String.valueOf(minePid);
            } else {
                value = "1";
            }
            first = false;
            matcher.appendReplacement(url, value);
        }
        matcher.appendTail(url);
        return url.toString();
    }

    private String refusal(String who, String pattern, String verb) throws Exception {
        MvcResult result = mockMvc.perform(request(HttpMethod.valueOf(verb), url(pattern)).with(user(who)).with(csrf())).andReturn();
        Exception error = result.getResolvedException();
        return error == null ? null : error.getMessage();
    }

    private static boolean allowed(String pattern) {
        return ALLOWED.contains(pattern) || ALLOWED_PREFIX.matcher(pattern).matches();
    }

    @Test
    @DisplayName("Игрок не на своём ходу: отказ везде, кроме разрешённого списка")
    void playerOffTurn() throws Exception {
        List<String[]> routes = new ArrayList<>(routes());
        assertThat(routes).hasSizeGreaterThan(60);
        // разрешённые меняют бой (Fast Draw переставляет очередь) — их в конце
        routes.sort(java.util.Comparator.comparing(route -> allowed(route[0])));
        List<String> leaked = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        for (String[] route : routes) {
            String message = refusal("kolya", route[0], route[1]);
            boolean refused = RoundActions.NOT_YOUR_TURN.equals(message);
            if (allowed(route[0]) && refused) {
                blocked.add(route[1] + " " + route[0]);
            } else if (!allowed(route[0]) && !refused) {
                leaked.add(route[1] + " " + route[0] + " -> " + message);
            }
            entityManager.clear();
        }
        // мастерские маршруты игроку закрыты раньше — 404 от AccessInterceptor, а не «не ваш ход»
        leaked.removeIf(route -> route.replaceAll(" -> .*", "").matches(".*/encounter/(start|end|attach|save-map|map/doc|participants|map/tokens|"
                + "initiative-mode|fog|loot|participants/\\{}/(heal|actions|remove|initiative|effects/.*))$"));
        leaked.removeIf(route -> route.replaceAll(" -> .*", "").contains("/npc/"));
        assertThat(leaked).as("вне хода проходит").isEmpty();
        assertThat(blocked).as("разрешено, но закрыто").isEmpty();
    }

    @Test
    @DisplayName("Мастер делает всё и вне хода персонажа")
    void master() throws Exception {
        for (String[] route : routes()) {
            assertThat(refusal("master", route[0], route[1])).as(route[1] + " " + route[0]).isNotEqualTo(RoundActions.NOT_YOUR_TURN);
            entityManager.clear();
        }
    }

    @Test
    @DisplayName("Лист вне хода — подпись «только для просмотра» игроку, мастеру нет")
    void sheetBanner() throws Exception {
        String sheet = "/character/" + mine.getId();
        assertThat(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(sheet).with(user("kolya")))
                .andReturn().getResponse().getContentAsString()).contains("лист только для просмотра");
        assertThat(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(sheet).with(user("master")))
                .andReturn().getResponse().getContentAsString()).doesNotContain("лист только для просмотра");
    }

    @Test
    @DisplayName("Не в бою — лист без проверки хода")
    void outOfFight() throws Exception {
        encounterService.end(campaign.getId());
        entityManager.flush();
        entityManager.clear();
        assertThat(refusal("kolya", "/character/{}/main-weapon", "POST")).isNotEqualTo(RoundActions.NOT_YOUR_TURN);
    }
}
