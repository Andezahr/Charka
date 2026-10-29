package app.charka.system.cp2020;

import app.charka.model.Campaign;
import app.charka.system.cp2020.Cp2020ArmorStrategy;
import app.charka.system.cp2020.Cp2020HealthStrategy;
import app.charka.system.cp2020.Cp2020StatsStrategy;
import app.charka.service.counter.HealthCounterService;
import app.charka.system.Currency;
import app.charka.system.GameSystem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cyberpunk 2020 (Friday Night Firefight).
 */
@Component
@RequiredArgsConstructor
public class Cp2020System implements GameSystem {

    public static final String CODE = "CP2020";

    private static final Currency CURRENCY = new Currency("Евродоллары", "eb");

    private final HealthCounterService healthCounterService;

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getDisplayName() {
        return "Cyberpunk 2020";
    }

    @Override
    public Currency getCurrency() {
        return CURRENCY;
    }

    @Override
    public List<String> getDefaultCounterTypes() {
        return List.of(Cp2020StatsStrategy.TYPE, Cp2020ArmorStrategy.TYPE, Cp2020HealthStrategy.TYPE);
    }

    /**
     * Естественное лечение: 1 очко в день (правила — в {@link Cp2020HealthStrategy#healOverTime}).
     */
    @Override
    public void onTimePassed(Campaign campaign, int days) {
        healthCounterService.healOverTime(campaign.getId(), days);
    }
}
