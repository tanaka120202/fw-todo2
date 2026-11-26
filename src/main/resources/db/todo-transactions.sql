CREATE TABLE IF NOT EXISTS todo_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operation_id CHAR(36) NOT NULL,
    todo_id BIGINT NOT NULL,
    operation VARCHAR(16) NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_todo_history_todo_id (todo_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS todo_audit_log (
    operation_id CHAR(36) NOT NULL PRIMARY KEY,
    operation VARCHAR(16) NOT NULL,
    requested_todo_id BIGINT NULL,
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
