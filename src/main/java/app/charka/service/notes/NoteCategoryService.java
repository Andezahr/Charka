package app.charka.service.notes;

import app.charka.model.NoteCategory;
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

    private static final String CATEGORY_NOT_FOUND = "Категория не найдена";


    @Transactional(readOnly = true)
    public List<NoteCategory> getAllCategories() {
        return categoryRepository.findAll();
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

    @Transactional
    public NoteCategory createCategory(NoteCategory category) {
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Имя категории не может быть пустым");
        }

        return categoryRepository.save(category);
    }


    @Transactional
    public NoteCategory updateCategory(Long id, NoteCategory updated) {
        NoteCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(CATEGORY_NOT_FOUND));
        category.setName(updated.getName());
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }
}
