package app.charka.controller;

import app.charka.model.notes.Note;
import app.charka.service.CharacterService;
import app.charka.service.notes.NoteCategoryService;
import app.charka.service.notes.NoteService;
import app.charka.model.Character;
import app.charka.model.notes.NoteCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/character/{characterId}/notes")
@RequiredArgsConstructor
public class NoteController {
    private final NoteService noteService;
    private final CharacterService characterService;
    private final NoteCategoryService categoryService;

    @GetMapping("/")
    public String listNotes(
            @PathVariable Long characterId,
            @RequestParam(required = false) Long categoryId,
            Model model
    ) {
        Character character = characterService.getById(characterId);

        List<Note> notes = (categoryId == null)
                ? noteService.getNotesByCharacter(characterId)
                : noteService.getNotesByCharacterAndCategory(characterId, categoryId);

        List<NoteCategory> categories = categoryService.getAllCategories();

        model.addAttribute("character", character);
        model.addAttribute("notes", notes);
        model.addAttribute("categories", categories);
        model.addAttribute("noteForm", new Note());
        model.addAttribute("categoryForm", new NoteCategory());

        return "notes";
    }

    @PostMapping
    public String addNote(
            @PathVariable Long characterId,
            @ModelAttribute("noteForm") Note noteForm,
            BindingResult bindingResult,
            @RequestParam(name = "categoryIds", required = false) Set<Long> categoryIds,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return listNotes(characterId, null, model);
        }

        Character character = characterService.getById(characterId);
        noteForm.setCharacter(character);

        Note saved = noteService.createNote(noteForm);

        if (categoryIds != null && !categoryIds.isEmpty()) {
            noteService.addCategoriesToNote(saved.getId(), categoryIds);
        }

        return "redirect:/character/" + characterId + "/notes";
    }

    @PostMapping("/{noteId}/edit")
    public String updateNote(
            @PathVariable Long characterId,
            @PathVariable Long noteId,
            @ModelAttribute("noteForm") Note noteForm,
            BindingResult bindingResult,
            @RequestParam(name = "categoryIds", required = false) Set<Long> categoryIds,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return listNotes(characterId, null, model);
        }

        noteService.updateNote(noteId, noteForm);

        noteService.removeCategoriesFromNote(noteId, categoryService.getAllCategoryIds());
        if (categoryIds != null && !categoryIds.isEmpty()) {
            noteService.addCategoriesToNote(noteId, categoryIds);
        }

        return "redirect:/character/" + characterId + "/notes";
    }

    @PostMapping("/{noteId}/delete")
    public String deleteNote(
            @PathVariable Long characterId,
            @PathVariable Long noteId
    ) {
        noteService.deleteNote(noteId);
        return "redirect:/character/" + characterId + "/notes";
    }

    @GetMapping("/{noteId}/edit")
    public String editNoteForm(
            @PathVariable Long characterId,
            @PathVariable Long noteId,
            Model model
    ) {
        Character character = characterService.getById(characterId);

        Note note = noteService.getNoteById(noteId);
        List<NoteCategory> categories = categoryService.getAllCategories();
        List<Note> notes = noteService.getNotesByCharacter(characterId);

        model.addAttribute("character", character);
        model.addAttribute("noteForm", note);
        model.addAttribute("categories", categories);
        model.addAttribute("notes", notes);
        model.addAttribute("categoryForm", new NoteCategory());

        return "notes";
    }
}
