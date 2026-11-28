-- Business operation log for administrator review. Records actor, action and target; no secrets or personal payloads.
CREATE TABLE IF NOT EXISTS operation_log (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_id VARCHAR(64),
    detail VARCHAR(512),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_oplog_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    INDEX ix_oplog_user_time (user_id, created_at),
    INDEX ix_oplog_action_time (action, created_at)
);
