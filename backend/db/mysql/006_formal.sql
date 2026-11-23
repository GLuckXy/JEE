CREATE TABLE IF NOT EXISTS fi_session (
    session_id VARCHAR(36) PRIMARY KEY,
    student_id VARCHAR(36) NOT NULL,
    role_id BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    ended_at BIGINT NOT NULL,
    end_reason VARCHAR(24),
    payload LONGTEXT NOT NULL,
    CONSTRAINT fk_formal_owner FOREIGN KEY (student_id) REFERENCES sys_user(id),
    INDEX ix_formal_owner_ended (student_id, ended_at)
);

CREATE TABLE IF NOT EXISTS fi_audio (
    session_id VARCHAR(36) NOT NULL,
    turn_id VARCHAR(36) NOT NULL,
    media_type VARCHAR(40) NOT NULL,
    recording LONGBLOB NOT NULL,
    PRIMARY KEY (session_id, turn_id),
    FOREIGN KEY (session_id) REFERENCES fi_session(session_id)
);
