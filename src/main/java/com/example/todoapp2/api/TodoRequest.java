package com.example.todoapp2.api;

import java.time.LocalDate;

import com.example.todoapp2.Todo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TodoRequest(
        @NotBlank(message = "{validation.title.required}")
        @Size(max = 255, message = "{validation.title.size}")
        String title,

        @Size(max = 255, message = "{validation.detail.size}")
        String detail,

        @NotBlank(message = "{validation.category.required}")
        @Size(max = 255, message = "{validation.category.size}")
        String category,

        @NotNull(message = "{validation.priority.required}")
        Integer priority,

        LocalDate dueDate,
        Boolean completed) {

    public Todo toTodo() {
        Todo todo = new Todo();
        todo.setTitle(title);
        todo.setDetail(detail);
        todo.setCategory(category);
        todo.setPriority(priority);
        todo.setDueDate(dueDate);
        todo.setCompleted(completed);
        return todo;
    }
}
