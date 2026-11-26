package com.example.todoapp2;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class TodoReportService {
    private final TodoService todos;

    public TodoReportService(TodoService todos) {
        this.todos = todos;
    }

    @Async("taskExecutor")
    public CompletableFuture<byte[]> generate(String keyword, String category, String order, AccountPrincipal actor) {
        var csv = new StringBuilder("\uFEFFid,title,category,priority,dueDate,completed\r\n");
        for (Todo todo : todos.search(keyword, category, order, null, null, actor)) {
            csv.append(todo.getId()).append(',')
                    .append(textCell(todo.getTitle())).append(',')
                    .append(textCell(todo.getCategory())).append(',')
                    .append(todo.getPriority() == null ? "" : todo.getPriority()).append(',')
                    .append(todo.getDueDate() == null ? "" : todo.getDueDate()).append(',')
                    .append(Boolean.TRUE.equals(todo.getCompleted())).append("\r\n");
        }
        return CompletableFuture.completedFuture(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String textCell(String value) {
        if (value == null) {
            return "\"\"";
        }
        String trimmed = value.stripLeading();
        if (!trimmed.isEmpty() && "=+-@＝＋－＠".indexOf(trimmed.charAt(0)) >= 0
                || !value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
