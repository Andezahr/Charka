package app.charka.service;

import app.charka.exception.NotFoundException;
import app.charka.model.Character;
import app.charka.model.Contact;
import app.charka.repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Контакты персонажа на странице заметок.
 */
@Service
@RequiredArgsConstructor
public class ContactService {

    private static final int MAX_TEXT = 255;
    private static final int MAX_NOTE = 1000;

    private final CharacterRepository characterRepository;

    /**
     * Новый контакт или правка существующего ({@code contactId} не null).
     *
     * @throws IllegalArgumentException если нет имени или текст длиннее допустимого
     * @throws NotFoundException        если контакта нет или он чужой
     */
    @Transactional
    public Contact save(Long characterId, Long contactId, String name, String role, Contact.Relation relation, String owes, String note) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Как зовут контакт?");
        }
        Character character = character(characterId);
        Contact contact = contactId == null ? new Contact() : find(character, contactId);
        contact.setName(limit(name, MAX_TEXT));
        contact.setRole(limit(role, MAX_TEXT));
        contact.setRelation(relation == null ? Contact.Relation.NEUTRAL : relation);
        contact.setOwes(limit(owes, MAX_TEXT));
        contact.setNote(limit(note, MAX_NOTE));
        if (contactId == null) {
            contact.setCharacter(character);
            character.getContacts().add(contact);
        }
        return contact;
    }

    @Transactional
    public void delete(Long characterId, Long contactId) {
        Character character = character(characterId);
        character.getContacts().remove(find(character, contactId));
    }

    private Character character(Long characterId) {
        return characterRepository.findById(characterId).orElseThrow(NotFoundException.forId("Character", characterId));
    }

    private static Contact find(Character character, Long contactId) {
        return character.getContacts().stream()
                .filter(contact -> contact.getId().equals(contactId))
                .findFirst()
                .orElseThrow(NotFoundException.forId("Contact", contactId));
    }

    private static String limit(String text, int max) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.strip();
        if (value.length() > max) {
            throw new IllegalArgumentException("Слишком длинно: не больше " + max + " знаков");
        }
        return value;
    }
}
