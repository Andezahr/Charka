package app.charka.controller;

import app.charka.model.Contact;
import app.charka.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Контакты на странице заметок.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/character/{characterId}/contacts")
public class ContactController {

    private final ContactService contactService;

    @PostMapping
    public String create(@PathVariable Long characterId, @RequestParam String name, @RequestParam(required = false) String role,
                         @RequestParam(required = false) Contact.Relation relation, @RequestParam(required = false) String owes,
                         @RequestParam(required = false) String note, RedirectAttributes redirectAttributes) {
        return save(characterId, null, name, role, relation, owes, note, redirectAttributes);
    }

    @PostMapping("/{contactId}/edit")
    public String edit(@PathVariable Long characterId, @PathVariable Long contactId, @RequestParam String name,
                       @RequestParam(required = false) String role, @RequestParam(required = false) Contact.Relation relation,
                       @RequestParam(required = false) String owes, @RequestParam(required = false) String note,
                       RedirectAttributes redirectAttributes) {
        return save(characterId, contactId, name, role, relation, owes, note, redirectAttributes);
    }

    @PostMapping("/{contactId}/delete")
    public String delete(@PathVariable Long characterId, @PathVariable Long contactId) {
        contactService.delete(characterId, contactId);
        return back(characterId);
    }

    private String save(Long characterId, Long contactId, String name, String role, Contact.Relation relation, String owes,
                        String note, RedirectAttributes redirectAttributes) {
        try {
            contactService.save(characterId, contactId, name, role, relation, owes, note);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return back(characterId);
    }

    private static String back(Long characterId) {
        return "redirect:/character/" + characterId + "/notes#contacts";
    }
}
