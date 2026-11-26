package com.example.todoapp2;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class TodoService {
    private final TodoMapper todoMapper;

    private final TodoChangeMapper changes;
    private final TodoAuditService audit;
    private final TodoAttachmentService attachments;

    public TodoService(TodoMapper todoMapper, TodoChangeMapper changes, TodoAuditService audit,
                       TodoAttachmentService attachments) {
        this.todoMapper = todoMapper;
        this.changes = changes;
        this.audit = audit;
        this.attachments = attachments;
    }

    @Transactional(readOnly = true)
    public List<Todo> search(String keyword, String category, String order) {
        return search(keyword, category, order, null, null);
    }

    @Transactional(readOnly = true)
    public List<Todo> search(String keyword, String category, String order, LocalDate from, LocalDate to) {
        return search(keyword, category, order, from, to, currentAccount());
    }

    @Transactional(readOnly = true)
    public List<Todo> search(String keyword, String category, String order, LocalDate from, LocalDate to,
                             AccountPrincipal account) {
        return todoMapper.search(keyword, category, order, from, to, ownerId(account));
    }

    @Transactional(readOnly = true)
    public Todo findById(Long id) {
        return findById(id, currentAccount());
    }

    @Transactional(readOnly = true)
    public Todo findById(Long id, AccountPrincipal account) {
        return todoMapper.findById(id, ownerId(account));
    }

    public int create(Todo todo) {
        AccountPrincipal account = currentAccount();
        String operationId = audit.recordAttempt("CREATE", null);
        int result = todoMapper.insert(todo, account.getId());
        if (result > 0) {
            changes.insertHistory(operationId, todo.getId(), "CREATE");
        }
        log.info("登録: id={}", todo.getId());
        return result;
    }

    public int update(Todo todo) {
        Long ownerId = ownerId(currentAccount());
        String operationId = audit.recordAttempt("UPDATE", todo.getId());
        int result = todoMapper.update(todo, ownerId);
        if (result > 0) {
            changes.insertHistory(operationId, todo.getId(), "UPDATE");
            log.info("編集: id={}", todo.getId());
        }
        return result;
    }

    public int delete(Long id) {
        Long ownerId = ownerId(currentAccount());
        String operationId = audit.recordAttempt("DELETE", id);
        List<Long> deletableIds = todoMapper.lockIds(List.of(id), ownerId);
        if (deletableIds.isEmpty()) {
            return 0;
        }
        attachments.deleteFilesAfterCommit(deletableIds);
        int result = todoMapper.deleteById(id, ownerId);
        if (result > 0) {
            changes.insertHistory(operationId, id, "DELETE");
            log.info("削除: id={}", id);
        }
        return result;
    }

    public int deleteByIds(List<Long> ids) {
        Long ownerId = ownerId(currentAccount());
        if (ids.isEmpty()) {
            return 0;
        }
        String operationId = audit.recordAttempt("BULK_DELETE", null);
        List<Long> deletableIds = todoMapper.lockIds(ids, ownerId);
        if (deletableIds.isEmpty()) {
            return 0;
        }
        attachments.deleteFilesAfterCommit(deletableIds);
        int deleted = todoMapper.deleteByIds(deletableIds, ownerId);
        for (Long id : deletableIds) {
            changes.insertHistory(operationId, id, "BULK_DELETE");
        }
        return deleted;
    }

    private Long ownerId(AccountPrincipal account) {
        return account.isAdmin() ? null : account.getId();
    }

    private AccountPrincipal currentAccount() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal account) {
            return account;
        }
        throw new AccessDeniedException("ログインが必要です。");
    }
}
