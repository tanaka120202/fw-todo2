package com.example.todoapp2;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

import jakarta.validation.Valid;

@Controller
public class HomeController {
    private final TodoService todoService;

    public HomeController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/todos")
    public String todos(@RequestParam(name = "keyword", defaultValue = "") String keyword,
                        @RequestParam(name = "category", defaultValue = "") String category,
                        @RequestParam(name = "order", defaultValue = "asc") String order,
                        Model model, @AuthenticationPrincipal AccountPrincipal account) {
        String sortOrder = "desc".equals(order) ? "desc" : "asc";
        model.addAttribute("todos", todoService.search(keyword, category, sortOrder));
        model.addAttribute("keyword", keyword);
        model.addAttribute("category", category);
        model.addAttribute("order", sortOrder);
        model.addAttribute("admin", account.isAdmin());
        return "todos";
    }

    @GetMapping("/todos/new")
    public String create(@ModelAttribute("todo") Todo todo) {
        return "create";
    }

    @PostMapping("/todos/confirm")
    public String confirm(@Valid @ModelAttribute("todo") Todo todo, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "create";
        }
        return "create-confirm";
    }

    @PostMapping("/todos/new")
    public String rewrite(@ModelAttribute("todo") Todo todo) {
        return "create";
    }

    @PostMapping("/todos")
    public String store(@ModelAttribute("todo") Todo todo, RedirectAttributes redirectAttributes) {
        todoService.create(todo);
        redirectAttributes.addFlashAttribute("message", "todo.created");
        return "redirect:/todos";
    }

    @GetMapping("/todos/{id}/edit")
    public String edit(@PathVariable("id") Long id, Model model) {
        Todo todo = todoService.findById(id);
        if (todo == null) {
            throw new TodoNotFoundException();
        }
        model.addAttribute("todo", todo);
        return "edit";
    }

    @PostMapping("/todos/{id}/confirm")
    public String confirmEdit(@PathVariable("id") Long id, @Valid @ModelAttribute("todo") Todo todo, BindingResult bindingResult) {
        todo.setId(id);
        if (bindingResult.hasErrors()) {
            return "edit";
        }
        return "edit-confirm";
    }

    @PostMapping("/todos/{id}/edit")
    public String rewriteEdit(@PathVariable("id") Long id, @ModelAttribute("todo") Todo todo) {
        todo.setId(id);
        return "edit";
    }

    @PostMapping("/todos/{id}")
    public String update(@PathVariable("id") Long id, @ModelAttribute("todo") Todo todo, RedirectAttributes redirectAttributes) {
        todo.setId(id);
        int updated = todoService.update(todo);
        if (updated == 0) {
            throw new TodoNotFoundException();
        }
        redirectAttributes.addFlashAttribute("message", "todo.updated");
        return "redirect:/todos";
    }

    @GetMapping("/todos/{id}/delete")
    public String confirmDelete(@PathVariable("id") Long id, Model model) {
        Todo todo = todoService.findById(id);
        if (todo == null) {
            throw new TodoNotFoundException();
        }
        model.addAttribute("todo", todo);
        return "delete";
    }

    @PostMapping("/todos/{id}/delete")
    public String delete(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        int deleted = todoService.delete(id);
        if (deleted == 0) {
            throw new TodoNotFoundException();
        }
        redirectAttributes.addFlashAttribute("message", "todo.deleted");
        return "redirect:/todos";
    }

    @PostMapping("/todos/delete")
    public String deleteSelected(@RequestParam(name = "ids", required = false) List<Long> ids,
                                 RedirectAttributes redirectAttributes) {
        if (ids != null && !ids.isEmpty()) {
            todoService.deleteByIds(ids);
            redirectAttributes.addFlashAttribute("message", "todo.bulk.deleted");
        }
        return "redirect:/todos";
    }
}
