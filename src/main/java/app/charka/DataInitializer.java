package app.charka;

import app.charka.model.*;
import app.charka.model.Character;
import app.charka.repository.CampaignRepository;
import app.charka.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;



@Component
public class DataInitializer implements CommandLineRunner {

    private final CampaignRepository campaignRepository;
    private final InventoryRepository inventoryRepository;

    public DataInitializer(CampaignRepository campaignRepository, InventoryRepository inventoryRepository) {
        this.campaignRepository = campaignRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public void run(String... args) {
        Campaign campaign = new Campaign();
        campaign.setName("Последний шанс");
        campaign.setStartDate(LocalDate.of(2037, 10, 27));
        campaign.setCurrentDate(campaign.getStartDate());

        Chronicle chronicle = new Chronicle();
        campaign.setChronicles(List.of(chronicle));

        Item glasses = new Item();
        glasses.setName("Очки");

        Item glasses2 = new Item();
        glasses2.setName("Очки");

        Inventory kolyasickInventory = new Inventory();
        kolyasickInventory.setName("Инвентарь Колясика");
        kolyasickInventory.setContent(List.of(glasses));

        Inventory olejaInventory = new Inventory();
        olejaInventory.setName("Инвентарь Олежи");
        olejaInventory.setContent(List.of(glasses2));

        Character kolyasick = new Character();
        kolyasick.setName("Колясик");
        kolyasick.setCampaign(campaign);

        Character oleja = new Character();
        oleja.setName("Олежа");
        oleja.setCampaign(campaign);

        campaign.setCharacters(List.of(kolyasick, oleja));
        campaignRepository.save(campaign);


        kolyasick.setWounds(Collections.emptyList());
        kolyasick.setInventories(List.of(kolyasickInventory));

        oleja.setWounds(Collections.emptyList());
        oleja.setInventories(List.of(olejaInventory));


        inventoryRepository.save(kolyasickInventory);
    }
}
