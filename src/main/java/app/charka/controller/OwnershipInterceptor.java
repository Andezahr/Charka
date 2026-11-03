package app.charka.controller;

import app.charka.exception.NotFoundException;
import app.charka.repository.InventoryRepository;
import app.charka.repository.ItemRepository;
import app.charka.repository.MoneyRepository;
import app.charka.repository.NoteRepository;
import app.charka.repository.WoundRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Проверка принадлежности (U-02): инвентарь, предмет, деньги, заметка и рана из адреса {@code /character/{characterId}/...}
 * должны принадлежать этому персонажу, иначе 404 — как будто их нет. Так же проверяется инвентарь, куда переносят предмет
 * ({@code targetInventoryId}). Одна проверка на все маршруты, контроллеры о ней не знают.
 */
@Component
@RequiredArgsConstructor
public class OwnershipInterceptor implements HandlerInterceptor {

    private final InventoryRepository inventoryRepository;
    private final ItemRepository itemRepository;
    private final MoneyRepository moneyRepository;
    private final NoteRepository noteRepository;
    private final WoundRepository woundRepository;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        @SuppressWarnings("unchecked")
        Map<String, String> variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables == null || !variables.containsKey("characterId")) {
            return true;
        }
        Long characterId = id(variables.get("characterId"));
        if (characterId == null) {
            return true;
        }
        check("Inventory", variables.get("inventoryId"), characterId,
                id -> inventoryRepository.findById(id).map(inventory -> inventory.getCharacter().getId()));
        check("Inventory", request.getParameter("targetInventoryId"), characterId,
                id -> inventoryRepository.findById(id).map(inventory -> inventory.getCharacter().getId()));
        check("Item", variables.get("itemId"), characterId,
                id -> itemRepository.findById(id).map(item -> item.getInventory().getCharacter().getId()));
        check("Money", variables.get("moneyId"), characterId,
                id -> moneyRepository.findById(id).map(money -> money.getCharacter().getId()));
        check("Note", variables.get("noteId"), characterId,
                id -> noteRepository.findById(id).map(note -> note.getCharacter().getId()));
        check("Wound", variables.get("woundId"), characterId,
                id -> woundRepository.findById(id).map(wound -> wound.getCharacter().getId()));
        return true;
    }

    private interface Owner {
        Optional<Long> of(Long id);
    }

    private static void check(String entity, String raw, Long characterId, Owner owner) {
        Long id = id(raw);
        if (id == null) {
            return;
        }
        if (!owner.of(id).map(found -> Objects.equals(found, characterId)).orElse(false)) {
            throw NotFoundException.forId(entity, id).get();
        }
    }

    private static Long id(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
