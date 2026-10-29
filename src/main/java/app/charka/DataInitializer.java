package app.charka;

import app.charka.model.*;
import app.charka.model.Character;
import app.charka.repository.CampaignRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;



@Component
public class DataInitializer implements CommandLineRunner {

    private final CampaignRepository campaignRepository;

    public DataInitializer(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    @Override
    public void run(String... args) {
        // БД постоянная: демо-кампания нужна только при первом запуске
        if (campaignRepository.count() > 0) {
            return;
        }
        Campaign campaign = new Campaign();
        campaign.setName("Последний шанс");
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
        campaignRepository.save(campaign);
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
