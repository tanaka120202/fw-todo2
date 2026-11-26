package com.example.todoapp2;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TodoAuditService {
    private final TodoChangeMapper changes;

    public TodoAuditService(TodoChangeMapper changes) {
        this.changes = changes;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String recordAttempt(String operation, Long todoId) {
        String operationId = UUID.randomUUID().toString();
        changes.insertAudit(operationId, operation, todoId);
        return operationId;
    }
}
