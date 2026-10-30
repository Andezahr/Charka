package app.charka;

import app.charka.model.*;
import app.charka.model.Character;
import app.charka.repository.CampaignRepository;
import app.charka.service.counter.CounterService;
import app.charka.system.GameSystemRegistry;
import app.charka.system.cp2020.Cp2020System;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;



@Component
public class DataInitializer implements CommandLineRunner {

    private final CampaignRepository campaignRepository;
    private final CounterService counterService;
    private final GameSystemRegistry systemRegistry;

    public DataInitializer(CampaignRepository campaignRepository, CounterService counterService, GameSystemRegistry systemRegistry) {
        this.campaignRepository = campaignRepository;
        this.counterService = counterService;
        this.systemRegistry = systemRegistry;
    }

    @Override
    public void run(String... args) {
        // БД постоянная: демо-кампания нужна только при первом запуске
        if (campaignRepository.count() > 0) {
            return;
        }
        Campaign campaign = new Campaign();
        campaign.setName("Последний шанс");
        campaign.setSystem(Cp2020System.CODE);
        campaign.setStartDate(LocalDate.of(2037, 10, 27));
        campaign.setCurrentDate(campaign.getStartDate());
        campaign.setChronicles(new ArrayList<>());

        Character kolyasick = new Character();
        kolyasick.setName("Колясик");
        kolyasick.setCampaign(campaign);
        kolyasick.getInventories().add(createInventory("Инвентарь Колясика", kolyasick, "Очки"));

        Character oleja = new Character();
        oleja.setName("Олежа");
        oleja.setCampaign(campaign);
        oleja.getInventories().add(createInventory("Инвентарь Олежи", oleja, "Очки"));

        campaign.setCharacters(new ArrayList<>(List.of(kolyasick, oleja)));
        // персонажи, инвентари и предметы сохраняются каскадом
        Campaign saved = campaignRepository.save(campaign);
        // счётчики системы сразу: иначе демо-персонажи не могут ни стрелять, ни получать урон
        List<String> counters = systemRegistry.get(Cp2020System.CODE).getDefaultCounterTypes();
        saved.getCharacters().forEach(character -> counterService.createDefaults(character.getId(), counters));
    }

    private static Inventory createInventory(String name, Character owner, String itemName) {
        Inventory inventory = new Inventory();
        inventory.setName(name);
        inventory.setCharacter(owner);

        Item item = new Item();
        item.setName(itemName);
        item.setQuantity(1);
        item.setCost(0);
        item.setInventory(inventory);

        inventory.setContent(new ArrayList<>(List.of(item)));
        return inventory;
    }
}
