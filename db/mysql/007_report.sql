CREATE TABLE IF NOT EXISTS rp_evaluation_rule (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    mode_scope VARCHAR(16) NOT NULL,
    dimension_weights TEXT NOT NULL,
    enabled BOOLEAN NOT NULL,
    published BOOLEAN NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS rp_report (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    session_id VARCHAR(36) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    rule_snapshot TEXT,
    summary TEXT,
    generated_at BIGINT NOT NULL,
    CONSTRAINT fk_rp_report_owner FOREIGN KEY (user_id) REFERENCES sys_user(id),
    INDEX ix_rp_user_time (user_id, generated_at)
);

CREATE TABLE IF NOT EXISTS rp_answer_evaluation (
    id VARCHAR(36) PRIMARY KEY,
    report_id VARCHAR(36) NOT NULL,
    ordinal INT NOT NULL,
    snapshot LONGTEXT NOT NULL,
    score TEXT,
    CONSTRAINT fk_rp_evaluation_report FOREIGN KEY (report_id) REFERENCES rp_report(id),
    CONSTRAINT uk_rp_evaluation_order UNIQUE (report_id, ordinal)
);

CREATE TABLE IF NOT EXISTS rp_retrain_link (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    report_id VARCHAR(36) NOT NULL,
    answer_evaluation_id VARCHAR(36) NOT NULL,
    target_session_id VARCHAR(36) NOT NULL UNIQUE,
    created_at BIGINT NOT NULL,
    CONSTRAINT fk_rp_retrain_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_rp_retrain_report FOREIGN KEY (report_id) REFERENCES rp_report(id),
    CONSTRAINT fk_rp_retrain_answer FOREIGN KEY (answer_evaluation_id) REFERENCES rp_answer_evaluation(id),
    CONSTRAINT fk_rp_retrain_target FOREIGN KEY (target_session_id) REFERENCES training_session(id)
);
