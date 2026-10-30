package app.charka.controller;

import app.charka.item.ItemTypeDefinition;
import app.charka.model.Item;
import app.charka.service.ItemService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/character/{characterId}/inventories/{inventoryId}/items")
public class ItemController {

    private static final String PROPERTY_PREFIX = "prop.";

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /**
     * Значения полей типа приходят параметрами {@code prop.<ключ>}. Некорректный ввод — flash {@code errorMessage}.
     */
    @PostMapping
    public String addItem(
            @PathVariable Long characterId,
            @PathVariable Long inventoryId,
            @RequestParam String itemName,
            @RequestParam(required = false) Integer quantity,
            @RequestParam(required = false) Integer cost,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = ItemTypeDefinition.GENERIC_CODE) String itemType,
            @RequestParam Map<String, String> params,
            RedirectAttributes redirectAttributes
    ) {
        Item item = new Item();
        item.setName(itemName);
        item.setQuantity(quantity);
        item.setCost(cost);
        item.setDescription(description);
        return handle(characterId, redirectAttributes,
                () -> itemService.create(inventoryId, item, itemType, properties(params)));
    }

    /**
     * Предмет из справочника системы.
     */
    @PostMapping("/catalog")
    public String addFromCatalog(
            @PathVariable Long characterId,
            @PathVariable Long inventoryId,
            @RequestParam String catalogKey,
            @RequestParam(defaultValue = "1") int quantity,
            RedirectAttributes redirectAttributes
    ) {
        return handle(characterId, redirectAttributes,
                () -> itemService.addFromCatalog(inventoryId, catalogKey, quantity));
    }

    @PostMapping("/{itemId}/edit")
    public String editItem(
            @PathVariable Long characterId,
            @PathVariable Long inventoryId,
            @PathVariable Long itemId,
            @RequestParam String itemName,
            @RequestParam(required = false) Integer quantity,
            @RequestParam(required = false) Integer cost,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = ItemTypeDefinition.GENERIC_CODE) String itemType,
            @RequestParam Map<String, String> params,
            RedirectAttributes redirectAttributes
    ) {
        return handle(characterId, redirectAttributes,
                () -> itemService.update(itemId, itemName, quantity, cost, description, itemType, properties(params)));
    }

    /**
     * Действие системы над предметом (выстрел, перезарядка). Возвращает к строке предмета, поэтому итог показывается в ней же:
     * сообщение вверху страницы осталось бы за экраном.
     */
    @PostMapping("/{itemId}/action/{action}")
    public String performAction(
            @PathVariable Long characterId,
            @PathVariable Long inventoryId,
            @PathVariable Long itemId,
            @PathVariable String action,
            @RequestParam Map<String, String> params,
            RedirectAttributes redirectAttributes
    ) {
        redirectAttributes.addFlashAttribute("actionItemId", itemId);
        try {
            redirectAttributes.addFlashAttribute("actionResult", itemService.performAction(characterId, itemId, action, params));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("actionError", e.getMessage());
        }
        return "redirect:/character/" + characterId + "#item-" + itemId;
    }

    private static Map<String, String> properties(Map<String, String> params) {
        Map<String, String> properties = new HashMap<>();
        params.forEach((key, value) -> {
            if (key.startsWith(PROPERTY_PREFIX)) {
                properties.put(key.substring(PROPERTY_PREFIX.length()), value);
            }
        });
        return properties;
    }

    private static String handle(Long characterId, RedirectAttributes redirectAttributes, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/character/" + characterId;
    }

    @PostMapping("/{itemId}/delete")
    public String deleteItem(
            @PathVariable Long characterId,
            @PathVariable Long inventoryId,
            @PathVariable Long itemId
    ) {
        itemService.delete(itemId);
        return "redirect:/character/" + characterId;
    }

    @PostMapping("/{itemId}/move")
    public String moveItem(
            @PathVariable Long characterId,
            @PathVariable Long inventoryId,
            @PathVariable Long itemId,
            @RequestParam Long targetInventoryId
    ) {
        itemService.moveToInventory(itemId, targetInventoryId);
        return "redirect:/character/" + characterId;
    }
}
