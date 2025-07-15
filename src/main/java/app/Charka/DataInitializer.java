package app.Charka;

import app.Charka.model.*;
import app.Charka.model.Character;
import app.Charka.repository.CampaignRepository;
import app.Charka.repository.CharacterRepository;
import app.Charka.repository.InventoryRepository;
import app.Charka.repository.ItemRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
public class DataInitializer implements CommandLineRunner {

    private final CampaignRepository campaignRepository;


    public DataInitializer(CharacterRepository characterRepository, CampaignRepository campaignRepository, InventoryRepository inventoryRepository, ItemRepository itemRepository) {
        this.campaignRepository = campaignRepository;
    }



    @Override
    public void run(String... args) throws Exception {
        // Создаём кампанию
        Campaign campaign = new Campaign();
        campaign.setName("Последний шанс");
        LocalDate date = LocalDate.of(2037, 10, 27);
        campaign.setStartDate(date);

        Chronicle chronicle = new Chronicle();

        campaign.setChronicles(List.of(chronicle));

        Item glasses = new Item();
        glasses.setName("Очки");

        Inventory kolyasickInventory = new Inventory();
        kolyasickInventory.setName("Инвентарь Колясика");
        kolyasickInventory.setContent(List.of(glasses));

        Item glasses2 = new Item();
        glasses2.setName("Очки");
        Inventory olejaInventory = new Inventory();
        olejaInventory.setName("Инвентарь Олежи");
        olejaInventory.setContent(List.of(glasses2));

        Character kolyasick = new Character();
        kolyasick.setName("Колясик");
        kolyasick.setWounds(Collections.emptyList());
        kolyasick.setInventories(List.of(olejaInventory));
        kolyasick.setCampaign(campaign);

        Character oleja = new Character();
        oleja.setName("Олежа");
        oleja.setWounds(Collections.emptyList());
        oleja.setInventories(List.of(olejaInventory));
        oleja.setCampaign(campaign);

        campaign.setCharacters(List.of(kolyasick, oleja));

        campaignRepository.save(campaign);
    }
}

