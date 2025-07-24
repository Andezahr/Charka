package app.charka.controller;

import app.charka.model.notes.NoteCategory;
import app.charka.service.notes.NoteCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


@Controller
@RequestMapping("/character/{characterId}/categories")
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
        return "redirect:/character/" + characterId + "/notes";
    }

    @PostMapping("/{categoryId}/edit")
    public String updateCategory(
            @PathVariable Long characterId,
            @PathVariable Long categoryId,
            @ModelAttribute("categoryForm") NoteCategory categoryForm,
            Model model
    ) {
        categoryService.updateCategory(categoryId, categoryForm);
        return "redirect:/character/" + characterId + "/notes";
    }

    @PostMapping("/{categoryId}/delete")
    public String deleteCategory(
            @PathVariable Long characterId,
            @PathVariable Long categoryId
    ) {
        categoryService.deleteCategory(categoryId);
        return "redirect:/character/" + characterId + "/notes";
    }
}
