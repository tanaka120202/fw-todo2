package com.example.todoapp2;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final UserManagementService users;

    public AdminUserController(UserManagementService users) {
        this.users = users;
    }

    @GetMapping
    public String list(Model model, @AuthenticationPrincipal AccountPrincipal actor) {
        model.addAttribute("users", users.list());
        model.addAttribute("actorId", actor.getId());
        return "admin/users";
    }

    @PostMapping
    public String create(@RequestParam String username, @RequestParam String password, RedirectAttributes redirect) {
        try {
            users.create(username, password);
            redirect.addFlashAttribute("message", "user.created");
        } catch (IllegalArgumentException invalid) {
            redirect.addFlashAttribute("error", invalid.getMessage());
            redirect.addFlashAttribute("username", username);
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        model.addAttribute("user", users.find(id));
        return "admin/user-edit";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable long id, @RequestParam UserAccount.Role role,
                       @RequestParam(defaultValue = "false") boolean enabled,
                       @RequestParam(defaultValue = "") String password, RedirectAttributes redirect) {
        try {
            users.edit(id, role, enabled, password);
            redirect.addFlashAttribute("message", "user.updated");
            return "redirect:/admin/users";
        } catch (IllegalArgumentException invalid) {
            redirect.addFlashAttribute("error", invalid.getMessage());
            redirect.addAttribute("id", id);
            return "redirect:/admin/users/{id}/edit";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal actor,
                         RedirectAttributes redirect) {
        try {
            users.delete(id, actor);
            redirect.addFlashAttribute("message", "user.deleted");
        } catch (IllegalArgumentException invalid) {
            redirect.addFlashAttribute("error", invalid.getMessage());
        }
        return "redirect:/admin/users";
    }
}
