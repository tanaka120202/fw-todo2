package com.example.todoapp2.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.todoapp2.Todo;
import com.example.todoapp2.TodoNotFoundException;
import com.example.todoapp2.TodoService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
public class TodoApiController {
    public static final String BASE_PATH = "/api/todo";
    private static final String LEGACY_PATH = "/api/todos";

    private final TodoService todoService;

    private final MessageSource messages;

    public TodoApiController(TodoService todoService, MessageSource messages) {
        this.todoService = todoService;
        this.messages = messages;
    }

    @GetMapping(value = {BASE_PATH, LEGACY_PATH}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> todos(HttpServletRequest httpRequest,
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @RequestParam(name = "category", defaultValue = "") String category,
            @RequestParam(name = "order", defaultValue = "asc") String order,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<TodoDto> todos = todoService.search(keyword, category, order, from, to).stream()
                .map(TodoDto::from)
                .toList();
        return ResponseEntity.ok(successBody(httpRequest, "api.list", todos));
    }

    @GetMapping({BASE_PATH + "/{id}", LEGACY_PATH + "/{id}"})
    public ResponseEntity<?> get(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        Todo todo = todoService.findById(id);
        if (todo == null) {
            throw new TodoNotFoundException();
        }
        return ResponseEntity.ok(successBody(httpRequest, "api.get", TodoDto.from(todo)));
    }

    @PostMapping({BASE_PATH, LEGACY_PATH})
    public ResponseEntity<?> create(@Valid @RequestBody TodoRequest request, HttpServletRequest httpRequest) {
        Todo todo = request.toTodo();
        todoService.create(todo);
        Long id = todo.getId();
        Todo saved = todoService.findById(id);
        return ResponseEntity.created(todoUri(httpRequest, id))
                .body(successBody(httpRequest, "api.create", TodoDto.from(saved)));
    }

    @PutMapping({BASE_PATH + "/{id}", LEGACY_PATH + "/{id}"})
    public ResponseEntity<?> update(@PathVariable("id") Long id, @Valid @RequestBody TodoRequest request,
                                    HttpServletRequest httpRequest) {
        if (todoService.findById(id) == null) {
            throw new TodoNotFoundException();
        }
        Todo todo = request.toTodo();
        todo.setId(id);
        todo.setCompleted(Boolean.TRUE.equals(request.completed()));
        todoService.update(todo);
        Todo saved = todoService.findById(id);
        if (saved == null) {
            throw new TodoNotFoundException();
        }
        return ResponseEntity.ok(successBody(httpRequest, "api.update", TodoDto.from(saved)));
    }

    @DeleteMapping({BASE_PATH + "/{id}", LEGACY_PATH + "/{id}"})
    public ResponseEntity<?> delete(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        if (todoService.delete(id) == 0) {
            throw new TodoNotFoundException();
        }
        return ResponseEntity.noContent().build();
    }

    private Object successBody(HttpServletRequest request, String messageKey, Object data) {
        return usesEnvelope(request)
                ? new TodoApiResponse<>(true, messages.getMessage(messageKey, null, LocaleContextHolder.getLocale()), data)
                : data;
    }

    private boolean usesEnvelope(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals(BASE_PATH) || path.startsWith(BASE_PATH + "/");
    }

    private URI todoUri(HttpServletRequest request, Long id) {
        String path = usesEnvelope(request) ? BASE_PATH : LEGACY_PATH;
        return URI.create(request.getContextPath() + path + "/" + id);
    }
}
