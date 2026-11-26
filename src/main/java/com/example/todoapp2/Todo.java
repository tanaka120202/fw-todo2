package com.example.todoapp2;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class Todo {
    private Long id;
    @NotBlank(message = "{validation.title.required}")
    @Size(max = 255, message = "{validation.title.size}")
    private String title;
    @Size(max = 255, message = "{validation.detail.size}")
    private String detail;
    @NotBlank(message = "{validation.category.required}")
    private String category;
    @NotNull(message = "{validation.priority.required}")
    private Integer priority;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;
    private Boolean completed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
