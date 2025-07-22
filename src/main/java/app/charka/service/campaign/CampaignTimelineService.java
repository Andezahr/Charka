package app.charka.service.campaign;

import app.charka.model.Campaign;
import app.charka.model.Chronicle;
import app.charka.service.ChronicleService;
import app.charka.service.MoneyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Сервис для управления ходом времени в кампании.
 * Отвечает за:
 * - создание и сохранение новых записей Chroniсle через ChronicleService;
 * - обновление текущей даты кампании;
 * - координацию бизнес-логики автоматических трат через MoneyService;
 */
@Service
@RequiredArgsConstructor
public class CampaignTimelineService {

    private final ChronicleService chronicleService;
    private final MoneyService moneyService;
    private final CampaignService campaignService;

    /**
     * Переводит кампанию вперед на заданное количество дней,
     * добавляет запись в хронику, обновляет дату кампании
     * и выполняет дополнительные операции (траты, лечение ран).
     *
     * @param campaignId  ID кампании, для которой происходит расчет
     * @param daysToSkip  количество дней для пропуска (>=1)
     * @param description описание события в хронике
     */
    @Transactional
    public void proceedDate(Long campaignId, int daysToSkip, String description) {
        Campaign campaign = campaignService.getById(campaignId).orElseThrow();
        LocalDate oldDate = campaign.getCurrentDate();
        LocalDate newDate = oldDate.plusDays(daysToSkip);

        // Создаем и сохраняем хронику периода
        Chronicle chronicle = new Chronicle();
        chronicle.setCampaign(campaign);
        chronicle.setName(description);
        chronicle.setStartDate(oldDate);
        chronicle.setEndDate(newDate);
        chronicleService.create(campaign.getId(), chronicle);

        campaignService.updateCurrentDate(campaign.getId(), newDate);

        // Реализовать периодические траты
    }
}

