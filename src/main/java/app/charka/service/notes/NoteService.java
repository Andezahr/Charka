package app.charka.service.notes;

import app.charka.model.notes.Note;
import app.charka.model.notes.NoteCategory;
import app.charka.repository.NoteCategoryRepository;
import app.charka.repository.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NoteService {

    private final NoteRepository noteRepository;
    private final NoteCategoryRepository categoryRepository;

    public NoteService(NoteRepository noteRepository, NoteCategoryRepository categoryRepository) {
        this.noteRepository = noteRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public Note createNote(Note note) {
        return noteRepository.save(note);
    }

    @Transactional
    public Note updateNote(Long noteId, Note updatedNote) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException(NOTE_NOT_FOUND));

        note.setName(updatedNote.getName());
        note.setText(updatedNote.getText());

        return noteRepository.save(note);
    }

    @Transactional
    public Note addCategoriesToNote(Long noteId, Set<Long> categoryIds) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException(NOTE_NOT_FOUND));

        Set<NoteCategory> categories = categoryIds.stream()
                .map(categoryRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        note.setCategories(new HashSet<>(categories));

        return noteRepository.save(note);
    }



    @Transactional
    public Note removeCategoriesFromNote(Long noteId, Set<Long> categoryIds) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException(NOTE_NOT_FOUND));

        categoryIds.forEach(categoryId -> {
            NoteCategory category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new RuntimeException(CATEGORY_NOT_FOUND));
            note.getCategories().remove(category);
        });

        return noteRepository.save(note);
    }

    @Transactional
    public void deleteNote(Long noteId) {
        noteRepository.deleteById(noteId);
    }

    private static final String NOTE_NOT_FOUND = "Заметка не найдена";
    private static final String CATEGORY_NOT_FOUND = "Категория не найдена";

    public List<Note> getNotesByCharacter(Long characterId) {
        return noteRepository.findByCharacterWithCategories(characterId);
    }

    public List<Note> getNotesByCharacterAndCategory(Long characterId, Long categoryId) {
        return noteRepository.findByCharacterAndCategoryWithCategories(characterId, categoryId);
    }

    public Note getNoteById(Long noteId) {
        Optional<Note> note = noteRepository.findById(noteId);
        return note.orElse(null);
    }

}
