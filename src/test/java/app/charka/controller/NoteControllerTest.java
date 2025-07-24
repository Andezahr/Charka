package app.charka.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import app.charka.GlobalExceptionHandler;
import app.charka.model.Character;
import app.charka.model.notes.Note;
import app.charka.model.notes.NoteCategory;
import app.charka.service.CharacterService;
import app.charka.service.notes.NoteCategoryService;
import app.charka.service.notes.NoteService;

@ExtendWith(MockitoExtension.class)
@DisplayName("NoteController – unit-тесты через Standalone MockMvc")
class NoteControllerTest {

    @Mock private NoteService noteService;
    @Mock private CharacterService characterService;
    @Mock private NoteCategoryService categoryService;
    @InjectMocks private NoteController noteController;

    private MockMvc mockMvc;

    private static final long CHAR_ID    = 1L;
    private static final long NOTE_ID    = 2L;
    private static final long CAT_ID     = 3L;
    private static final String REDIRECT = "/character/" + CHAR_ID + "/notes";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(noteController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ======================================================================
    //                            LIST NOTES
    // ======================================================================
    @Nested
    @DisplayName("Просмотр заметок")
    class ListNotesTests {
        @Test
        @DisplayName("200 – без фильтра")
        void listNotes_noCategory() throws Exception {
            Character character = new Character(); character.setId(CHAR_ID);
            List<Note> notes = List.of(new Note());
            List<NoteCategory> cats = List.of(new NoteCategory());

            when(characterService.getById(CHAR_ID)).thenReturn(Optional.of(character));
            when(noteService.getNotesByCharacter(CHAR_ID)).thenReturn(notes);
            when(categoryService.getAllCategories()).thenReturn(cats);

            mockMvc.perform(get("/character/{characterId}/notes/", CHAR_ID))
                    .andExpect(status().isOk())
                    .andExpect(view().name("notes"))
                    .andExpect(model().attribute("character", character))
                    .andExpect(model().attribute("notes", notes))
                    .andExpect(model().attribute("categories", cats))
                    .andExpect(model().attributeExists("noteForm"))
                    .andExpect(model().attributeExists("categoryForm"));

            verify(characterService).getById(CHAR_ID);
            verify(noteService).getNotesByCharacter(CHAR_ID);
            verify(categoryService).getAllCategories();
            verifyNoMoreInteractions(characterService, noteService, categoryService);
        }

        @Test
        @DisplayName("200 – с фильтром по категории")
        void listNotes_withCategory() throws Exception {
            Character character = new Character(); character.setId(CHAR_ID);
            List<Note> notes = List.of(new Note());
            List<NoteCategory> cats = List.of(new NoteCategory());

            when(characterService.getById(CHAR_ID)).thenReturn(Optional.of(character));
            when(noteService.getNotesByCharacterAndCategory(CHAR_ID, CAT_ID)).thenReturn(notes);
            when(categoryService.getAllCategories()).thenReturn(cats);

            mockMvc.perform(get("/character/{characterId}/notes/", CHAR_ID)
                            .param("categoryId", String.valueOf(CAT_ID)))
                    .andExpect(status().isOk())
                    .andExpect(view().name("notes"))
                    .andExpect(model().attribute("notes", notes));

            verify(characterService).getById(CHAR_ID);
            verify(noteService).getNotesByCharacterAndCategory(CHAR_ID, CAT_ID);
            verify(categoryService).getAllCategories();
            verifyNoMoreInteractions(characterService, noteService, categoryService);
        }

        @Test
        @DisplayName("500 – персонаж не найден")
        void listNotes_characterNotFound() throws Exception {
            when(characterService.getById(CHAR_ID)).thenReturn(Optional.empty());

            mockMvc.perform(get("/character/{characterId}/notes/", CHAR_ID))
                    .andExpect(status().isInternalServerError());

            verify(characterService).getById(CHAR_ID);
            verifyNoMoreInteractions(characterService);
        }
    }

    // ======================================================================
    //                            ADD NOTE
    // ======================================================================
    @Nested
    @DisplayName("Добавление заметки")
    class AddNoteTests {
        @Test
        @DisplayName("302 – без категорий")
        void addNote_noCategories() throws Exception {
            when(characterService.getById(CHAR_ID)).thenReturn(Optional.of(new Character()));
            when(noteService.createNote(any())).thenReturn(new Note());

            mockMvc.perform(post("/character/{characterId}/notes", CHAR_ID)
                            .param("content", "Текст")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(REDIRECT));

            verify(noteService).createNote(any());
            verifyNoMoreInteractions(noteService);
        }

        @Test
        @DisplayName("302 – с категориями")
        void addNote_withCategories() throws Exception {
            when(characterService.getById(CHAR_ID)).thenReturn(Optional.of(new Character()));
            Note saved = new Note(); saved.setId(NOTE_ID);
            when(noteService.createNote(any())).thenReturn(saved);

            Set<Long> cats = new HashSet<>(Set.of(CAT_ID));

            mockMvc.perform(post("/character/{characterId}/notes", CHAR_ID)
                            .param("content", "X")
                            .param("categoryIds", String.valueOf(CAT_ID))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(REDIRECT));

            verify(noteService).createNote(any());
            verify(noteService).addCategoriesToNote(NOTE_ID, cats);
            verifyNoMoreInteractions(noteService);
        }

        @Test
        @DisplayName("500 – сервис бросает исключение")
        void addNote_serviceError() throws Exception {
            doThrow(new IllegalArgumentException("Character not found"))
                    .when(characterService).getById(CHAR_ID);

            mockMvc.perform(post("/character/{characterId}/notes", CHAR_ID)
                            .param("content", "Текст")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isInternalServerError());

            verify(characterService).getById(CHAR_ID);
            verifyNoMoreInteractions(characterService, noteService);
        }
    }

    // ======================================================================
    //                          UPDATE NOTE
    // ======================================================================
    @Nested
    @DisplayName("Редактирование заметки")
    class UpdateNoteTests {
        @Test
        @DisplayName("302 – без категорий")
        void updateNote_noCategories() throws Exception {
            mockMvc.perform(post("/character/{characterId}/notes/{noteId}/edit", CHAR_ID, NOTE_ID)
                            .param("content", "N")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(REDIRECT));

            verify(noteService).updateNote(eq(NOTE_ID), any(Note.class));
            verify(categoryService).getAllCategoryIds();
            verify(noteService).removeCategoriesFromNote(NOTE_ID, Set.of());
            verifyNoMoreInteractions(noteService, categoryService);
        }

        @Test
        @DisplayName("302 – с категориями")
        void updateNote_withCategories() throws Exception {
            Set<Long> allIds = Set.of(5L, CAT_ID);
            when(categoryService.getAllCategoryIds()).thenReturn(allIds);

            mockMvc.perform(post("/character/{characterId}/notes/{noteId}/edit", CHAR_ID, NOTE_ID)
                            .param("content", "N")
                            .param("categoryIds", String.valueOf(CAT_ID))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(REDIRECT));

            verify(noteService).updateNote(eq(NOTE_ID), any(Note.class));
            verify(categoryService).getAllCategoryIds();
            verify(noteService).removeCategoriesFromNote(NOTE_ID, allIds);
            verify(noteService).addCategoriesToNote(NOTE_ID, Set.of(CAT_ID));
            verifyNoMoreInteractions(noteService, categoryService);
        }
    }

    // ======================================================================
    //                          EDIT FORM
    // ======================================================================
    @Nested
    @DisplayName("Форма редактирования заметки")
    class EditFormTests {
        @Test
        @DisplayName("200 – возвращает форму редактирования")
        void editForm_success() throws Exception {
            Character character = new Character(); character.setId(CHAR_ID);
            Note note = new Note(); note.setId(NOTE_ID);
            List<NoteCategory> cats = List.of(new NoteCategory());
            List<Note> notes = List.of(new Note());

            when(characterService.getById(CHAR_ID)).thenReturn(Optional.of(character));
            when(noteService.getNoteById(NOTE_ID)).thenReturn(note);
            when(categoryService.getAllCategories()).thenReturn(cats);
            when(noteService.getNotesByCharacter(CHAR_ID)).thenReturn(notes);

            mockMvc.perform(get("/character/{characterId}/notes/{noteId}/edit", CHAR_ID, NOTE_ID))
                    .andExpect(status().isOk())
                    .andExpect(view().name("notes"))
                    .andExpect(model().attribute("character", character))
                    .andExpect(model().attribute("noteForm", note))
                    .andExpect(model().attribute("categories", cats))
                    .andExpect(model().attribute("notes", notes))
                    .andExpect(model().attributeExists("categoryForm"));

            verify(characterService).getById(CHAR_ID);
            verify(noteService).getNoteById(NOTE_ID);
            verify(categoryService).getAllCategories();
            verify(noteService).getNotesByCharacter(CHAR_ID);
            verifyNoMoreInteractions(characterService, noteService, categoryService);
        }

        @Test
        @DisplayName("500 – персонаж не найден")
        void editForm_characterNotFound() throws Exception {
            when(characterService.getById(CHAR_ID)).thenReturn(Optional.empty());

            mockMvc.perform(get("/character/{characterId}/notes/{noteId}/edit", CHAR_ID, NOTE_ID))
                    .andExpect(status().isInternalServerError());

            verify(characterService).getById(CHAR_ID);
            verifyNoMoreInteractions(characterService);
        }
    }

    // ======================================================================
    //                          DELETE NOTE
    // ======================================================================
    @Nested
    @DisplayName("Удаление заметки")
    class DeleteNoteTests {
        @Test
        @DisplayName("302 – успешное удаление")
        void deleteNote_success() throws Exception {
            mockMvc.perform(post("/character/{characterId}/notes/{noteId}/delete", CHAR_ID, NOTE_ID))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(REDIRECT));

            verify(noteService).deleteNote(NOTE_ID);
            verifyNoMoreInteractions(noteService);
        }
    }
}
