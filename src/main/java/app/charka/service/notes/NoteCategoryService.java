package app.charka.service.notes;

import app.charka.exception.NotFoundException;
import app.charka.model.notes.NoteCategory;
import app.charka.repository.NoteCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteCategoryService {

    private final NoteCategoryRepository categoryRepository;
    private final app.charka.repository.CharacterRepository characterRepository;

    private static final String CATEGORY_NOT_FOUND = "Категория не найдена";


    /** Категории персонажа и общие */
    @Transactional(readOnly = true)
    public List<NoteCategory> getForCharacter(Long characterId) {
        return categoryRepository.findByCharacterIdOrCharacterIsNullOrderByName(characterId);
    }

    @Transactional(readOnly = true)
    public Set<Long> getAllCategoryIds() {
        return categoryRepository.findAll()
                .stream()
                .map(NoteCategory::getId)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Optional<NoteCategory> getById(Long id) {
        return categoryRepository.findById(id);
    }

    /**
     * Новая категория персонажа.
     */
    @Transactional
    public NoteCategory createCategory(Long characterId, NoteCategory category) {
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Имя категории не может быть пустым");
        }
        category.setCharacter(characterRepository.findById(characterId)
                .orElseThrow(NotFoundException.forId("Character", characterId)));
        return categoryRepository.save(category);
    }

    @Transactional
    public NoteCategory updateCategory(Long characterId, Long id, NoteCategory updated) {
        NoteCategory category = owned(characterId, id);
        category.setName(updated.getName());
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Long characterId, Long id) {
        categoryRepository.delete(owned(characterId, id));
    }

    /** Своя или общая категория; чужая считается ненайденной */
    private NoteCategory owned(Long characterId, Long id) {
        return categoryRepository.findById(id)
                .filter(category -> category.getCharacter() == null || category.getCharacter().getId().equals(characterId))
                .orElseThrow(() -> new NotFoundException(CATEGORY_NOT_FOUND));
    }
}
