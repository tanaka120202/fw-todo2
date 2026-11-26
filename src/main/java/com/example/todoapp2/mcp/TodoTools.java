package com.example.todoapp2.mcp;

import java.time.LocalDate;
import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.example.todoapp2.AccountPrincipal;
import com.example.todoapp2.Todo;
import com.example.todoapp2.TodoService;
import com.example.todoapp2.api.TodoDto;

@Component
public class TodoTools {
    private final TodoService todoService;

    public TodoTools(TodoService todoService) {
        this.todoService = todoService;
    }

    @McpTool(name = "list_todos", description = "やることの一覧を返す（期間・ジャンルで絞れる）")
    public List<TodoDto> listTodos(
            McpSyncRequestContext context,
            @McpToolParam(description = "検索語", required = false) String keyword,
            @McpToolParam(description = "ジャンル", required = false) String category,
            @McpToolParam(description = "期限の開始日（yyyy-MM-dd）", required = false) String from,
            @McpToolParam(description = "期限の終了日（yyyy-MM-dd）", required = false) String to) {
        LocalDate fromDate = from == null ? null : LocalDate.parse(from);
        LocalDate toDate = to == null ? null : LocalDate.parse(to);
        return todoService.search(keyword, category, "asc", fromDate, toDate, account(context)).stream()
                .map(TodoDto::from)
                .toList();
    }

    @McpTool(name = "get_todo", description = "やることを1件返す")
    public TodoDto getTodo(McpSyncRequestContext context,
                           @McpToolParam(description = "やることのID", required = true) Long id) {
        Todo todo = todoService.findById(id, account(context));
        return todo == null ? null : TodoDto.from(todo);
    }

    private AccountPrincipal account(McpSyncRequestContext context) {
        if (context.transportContext().get(McpSecurityConfig.ACCOUNT) instanceof AccountPrincipal account) {
            return account;
        }
        throw new AccessDeniedException("ログインが必要です。");
    }
}

