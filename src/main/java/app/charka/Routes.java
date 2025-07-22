package app.charka;

public class Routes {

    private Routes() {
        // Utility class — prevent instantiation
    }

    public static final String CAMPAIGN_REDIRECT = "redirect:/campaign/";

    public static final String CHARACTER = "/character/{id}";
    public static final String CHARACTER_REDIRECT = "redirect:/character/";

    public static final String INVENTORY_ADD = "/character/{characterId}/inventories";
    public static final String INVENTORY_EDIT = "/character/{characterId}/inventories/{inventoryId}/edit";
    public static final String INVENTORY_DELETE = "/character/{characterId}/inventories/{inventoryId}/delete";

    public static final String ITEM_ADD = "/character/{characterId}/inventories/{inventoryId}/items";
    public static final String ITEM_EDIT = "/character/{characterId}/inventories/{inventoryId}/items/{itemId}/edit";
    public static final String ITEM_DELETE = "/character/{characterId}/inventories/{inventoryId}/items/{itemId}/delete";
    public static final String ITEM_MOVE = "/character/{characterId}/inventories/{inventoryId}/items/{itemId}/move";

    public static final String WOUNDS_ADD = "/character/{characterId}/wounds";
    public static final String WOUNDS_DELETE = "/character/{characterId}/wounds/{woundId}/delete";

    public static final String MONEY_ADD = "/character/{characterId}/money";
    public static final String MONEY_DELETE = "/character/{characterId}/money/{moneyId}/delete";

    public static final String CHRONICLE_ADD = "/campaign/{campaignId}/chronicles";
    public static final String CHRONICLE_EDIT = "/campaign/{campaignId}/chronicles/{chronicleId}/edit";
    public static final String CHRONICLE_DELETE = "/campaign/{campaignId}/chronicles/{chronicleId}/delete";

    public static final String NOTE_ADD = "/character/{characterId}/notes";
    public static final String NOTE_REDIRECT = "redirect:/character/%d/notes";
    public static final String NOTE_EDIT = "/{noteId}/edit";
    public static final String NOTE_DELETE = "/{noteId}/delete";

    public static final String NOTE_CATEGORY_ADD = "/character/{characterId}/categories";
    public static final String NOTE_CATEGORY_EDIT = "/{categoryId}/edit";
    public static final String NOTE_CATEGORY_DELETE = "/{categoryId}/delete";

}
