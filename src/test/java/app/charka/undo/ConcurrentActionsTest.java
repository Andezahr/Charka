package app.charka.undo;

import app.charka.exception.GlobalExceptionHandler;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.counters.Counter;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CounterRepository;
import app.charka.repository.UndoEntryRepository;
import app.charka.service.CharacterService;
import app.charka.service.campaign.CampaignService;
import app.charka.support.CharkaTest;
import app.charka.system.kosmozeki.KzBody;
import app.charka.system.kosmozeki.KzBodyStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * VER-01: два телефона разом. Без общей транзакции теста — каждый запрос коммитит сам, как в жизни.
 */
@CharkaTest
@DisplayName("Одновременные действия: ничего не теряется (VER-01)")
class ConcurrentActionsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CampaignService campaignService;
    @Autowired private CharacterService characterService;
    @Autowired private CounterRepository counterRepository;
    @Autowired private UndoEntryRepository undoRepository;
    @Autowired private TransactionTemplate transactions;

    private Campaign campaign;
    private Character zek;
    private Long body;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Этап");
        created.setSystem("KOSMOZEKI");
        created.setStartDate(LocalDate.of(2300, 1, 1));
        created.setCurrentDate(LocalDate.of(2300, 1, 1));
        campaign = campaignRepository.save(created);
        Character character = new Character();
        character.setName("Зэк");
        zek = characterService.createInCampaign(campaign.getId(), character);
        body = counterRepository.findByCharacterId(zek.getId()).stream()
                .filter(counter -> KzBodyStrategy.TYPE.equals(counter.getType())).findFirst().orElseThrow().getId();
    }

    @AfterEach
    void tearDown() {
        campaignService.delete(campaign.getId());
    }

    private int lost() {
        return counterRepository.findById(body).orElseThrow().getValue();
    }

    private Callable<Integer> hit(CountDownLatch start) {
        return () -> {
            start.await();
            return mockMvc.perform(post("/character/" + zek.getId() + "/counters/" + body + "/health/damage")
                    .param("amount", "10").param("location", KzBody.BODY)).andReturn().getResponse().getStatus();
        };
    }

    /** Два удара одной цели в одно мгновение */
    private void twoHitsAtOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Integer> first = pool.submit(hit(start));
            Future<Integer> second = pool.submit(hit(start));
            start.countDown();
            assertThat(first.get()).isEqualTo(302);
            assertThat(second.get()).isEqualTo(302);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("Два урона одной цели разом — записаны оба")
    void bothHitsRecorded() throws Exception {
        twoHitsAtOnce();
        assertThat(lost()).isEqualTo(20);
    }

    @Test
    @DisplayName("Два действия разом, «↶» последнего — состояние после первого")
    void undoLastKeepsFirst() throws Exception {
        twoHitsAtOnce();
        Long last = undoRepository.findByCampaignIdOrderByIdDesc(campaign.getId()).getFirst().getId();
        mockMvc.perform(post("/campaign/" + campaign.getId() + "/undo/" + last));
        assertThat(lost()).isEqualTo(10);
    }

    @Test
    @DisplayName("Устаревшая запись — конфликт версий, на странице — подпись и назад, а не 500")
    void staleWrite() {
        Counter stale = transactions.execute(status -> counterRepository.findById(body).orElseThrow());
        transactions.executeWithoutResult(status -> counterRepository.findById(body).orElseThrow().setValue(5));
        stale.setValue(7);
        assertThatThrownBy(() -> counterRepository.save(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(lost()).isEqualTo(5);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/character/" + zek.getId() + "/money");
        FlashMap flash = new FlashMap();
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, flash);
        ModelAndView view = new GlobalExceptionHandler().handleConflict(
                new org.springframework.orm.ObjectOptimisticLockingFailureException(Counter.class, body), request);
        assertThat(view.getViewName()).isEqualTo("redirect:/character/" + zek.getId());
        assertThat(flash.get("errorMessage")).isEqualTo(GlobalExceptionHandler.CONFLICT);
    }
}
