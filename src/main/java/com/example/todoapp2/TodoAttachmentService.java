package com.example.todoapp2;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TodoAttachmentService {
    private final TodoMapper todos;
    private final TodoAttachmentMapper attachments;
    private final FileStorageService storage;
    private final long maxBytes;

    public TodoAttachmentService(TodoMapper todos, TodoAttachmentMapper attachments, FileStorageService storage,
                                 @Value("${spring.servlet.multipart.max-file-size}") DataSize maxSize) {
        this.todos = todos;
        this.attachments = attachments;
        this.storage = storage;
        this.maxBytes = maxSize.toBytes();
    }

    @Transactional(readOnly = true)
    public List<TodoAttachment> list(long todoId, AccountPrincipal actor) {
        if (todos.findById(todoId, ownerId(actor)) == null) {
            throw new TodoNotFoundException();
        }
        return attachments.list(todoId);
    }

    public void upload(long todoId, AccountPrincipal actor, MultipartFile file) {
        lockTodo(todoId, actor);
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE);
        }
        var attachment = new TodoAttachment();
        attachment.setTodoId(todoId);
        attachment.setOriginalName(storage.sanitizeFilename(file.getOriginalFilename()));
        attachment.setSizeBytes(file.getSize());
        attachment.setStorageName(storage.store(file));
        attachments.insert(attachment);
    }

    public Download download(long todoId, long attachmentId, AccountPrincipal actor) {
        lockTodo(todoId, actor);
        TodoAttachment attachment = find(todoId, attachmentId);
        return new Download(attachment.getOriginalName(), storage.read(attachment.getStorageName()));
    }

    public void delete(long todoId, long attachmentId, AccountPrincipal actor) {
        lockTodo(todoId, actor);
        TodoAttachment attachment = find(todoId, attachmentId);
        attachments.delete(todoId, attachmentId);
        storage.deleteAfterCommit(attachment.getStorageName());
    }

    void deleteFilesAfterCommit(List<Long> todoIds) {
        if (!todoIds.isEmpty()) {
            attachments.storageNames(todoIds).forEach(storage::deleteAfterCommit);
        }
    }

    private TodoAttachment find(long todoId, long attachmentId) {
        TodoAttachment attachment = attachments.find(todoId, attachmentId);
        if (attachment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return attachment;
    }

    private void lockTodo(long todoId, AccountPrincipal actor) {
        if (todos.lockIds(List.of(todoId), ownerId(actor)).isEmpty()) {
            throw new TodoNotFoundException();
        }
    }

    private Long ownerId(AccountPrincipal actor) {
        return actor.isAdmin() ? null : actor.getId();
    }

    public record Download(String filename, byte[] content) {
    }
}
