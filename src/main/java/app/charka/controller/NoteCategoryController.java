package app.charka.controller;

import app.charka.Routes;
import app.charka.model.NoteCategory;
import app.charka.service.notes.NoteCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


@Controller
@RequestMapping(Routes.NOTE_CATEGORY_ADD)
@RequiredArgsConstructor
public class NoteCategoryController {

    private final NoteCategoryService categoryService;

    @PostMapping
    public String addCategory(
            @PathVariable Long characterId,
            @ModelAttribute("categoryForm") NoteCategory categoryForm,
            Model model
    ) {
        categoryService.createCategory(categoryForm);
        return String.format(Routes.NOTE_REDIRECT, characterId);
    }

    @PostMapping(Routes.NOTE_CATEGORY_EDIT)
    public String updateCategory(
            @PathVariable Long characterId,
            @PathVariable Long categoryId,
            @ModelAttribute("categoryForm") NoteCategory categoryForm,
            Model model
    ) {
        categoryService.updateCategory(categoryId, categoryForm);
        return String.format(Routes.NOTE_REDIRECT, characterId);
    }

    @PostMapping(Routes.NOTE_CATEGORY_DELETE)
    public String deleteCategory(
            @PathVariable Long characterId,
            @PathVariable Long categoryId
    ) {
        categoryService.deleteCategory(categoryId);
        return String.format(Routes.NOTE_REDIRECT, characterId);
    }
}
