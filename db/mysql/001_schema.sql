-- Explicitly apply to an empty schema. No DROP statements, default users, passwords or automatic migrations.
-- All timestamps use UTC. MySQL 8.0.16+ is required for enforced CHECK constraints.

CREATE TABLE sys_user (
id VARCHAR(36) PRIMARY KEY,
account VARCHAR(16) NOT NULL,
password_hash VARCHAR(100) NOT NULL,
role VARCHAR(16) NOT NULL,
status VARCHAR(16) NOT NULL,
nickname VARCHAR(64) NOT NULL,
email VARCHAR(254),
phone VARCHAR(32),
school VARCHAR(128),
major VARCHAR(128),
updated_by VARCHAR(36) NOT NULL,
created_at TIMESTAMP(6) NOT NULL,
updated_at TIMESTAMP(6) NOT NULL,
CONSTRAINT uk_user_account UNIQUE (account),
CONSTRAINT ck_user_account_length CHECK (CHAR_LENGTH(account) BETWEEN 8 AND 16),
CONSTRAINT ck_user_role CHECK (role IN ('STUDENT','ADMIN')),
CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_session (
id VARCHAR(36) PRIMARY KEY,
user_id VARCHAR(36) NOT NULL,
expires_at TIMESTAMP(6) NOT NULL,
revoked_at TIMESTAMP(6),
created_at TIMESTAMP(6) NOT NULL,
CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE file_object (
id VARCHAR(36) PRIMARY KEY,
owner_id VARCHAR(36) NOT NULL,
storage_key VARCHAR(128) NOT NULL,
original_name VARCHAR(255) NOT NULL,
mime_type VARCHAR(128) NOT NULL,
file_size BIGINT NOT NULL,
biz_type VARCHAR(32) NOT NULL,
biz_id VARCHAR(36) NOT NULL,
storage_status VARCHAR(32) NOT NULL,
created_at TIMESTAMP(6) NOT NULL,
CONSTRAINT uk_file_storage_key UNIQUE (storage_key),
CONSTRAINT fk_file_owner FOREIGN KEY (owner_id) REFERENCES sys_user(id),
CONSTRAINT ck_file_size CHECK (file_size > 0),
CONSTRAINT ck_storage_status CHECK (storage_status IN ('WRITING','STORED','CLEANUP_REQUIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resume (
id VARCHAR(36) PRIMARY KEY,
user_id VARCHAR(36) NOT NULL,
file_id VARCHAR(36) NOT NULL,
parse_status VARCHAR(16) NOT NULL,
raw_parse_result LONGTEXT,
draft_payload LONGTEXT NOT NULL,
confirm_status VARCHAR(16) NOT NULL,
version INTEGER NOT NULL DEFAULT 0,
revision INTEGER NOT NULL DEFAULT 0,
failure_reason VARCHAR(512),
confirmed_at TIMESTAMP(6),
created_at TIMESTAMP(6) NOT NULL,
updated_at TIMESTAMP(6) NOT NULL,
CONSTRAINT uk_resume_file UNIQUE (file_id),
CONSTRAINT fk_resume_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
CONSTRAINT fk_resume_file FOREIGN KEY (file_id) REFERENCES file_object(id),
CONSTRAINT ck_parse_status CHECK (parse_status IN ('UPLOADED','PARSING','SUCCESS','FAILED')),
CONSTRAINT ck_confirm_status CHECK (confirm_status IN ('DRAFT','CONFIRMED')),
CONSTRAINT ck_resume_version CHECK (version >= 0 AND revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resume_version (
id VARCHAR(36) PRIMARY KEY,
resume_id VARCHAR(36) NOT NULL,
version INTEGER NOT NULL,
confirmed_payload LONGTEXT NOT NULL,
confirmed_at TIMESTAMP(6) NOT NULL,
CONSTRAINT uk_resume_version UNIQUE (resume_id,version),
CONSTRAINT uk_version_resume UNIQUE (id,resume_id),
CONSTRAINT fk_version_resume FOREIGN KEY (resume_id) REFERENCES resume(id),
CONSTRAINT ck_version_number CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resume_project (
id VARCHAR(36) PRIMARY KEY,
resume_id VARCHAR(36) NOT NULL,
version_id VARCHAR(36) NOT NULL,
name VARCHAR(256) NOT NULL,
goal LONGTEXT,
responsibility LONGTEXT NOT NULL,
tech_stack LONGTEXT NOT NULL,
difficulty LONGTEXT,
solution LONGTEXT,
result LONGTEXT,
improvement LONGTEXT,
sort_no INTEGER NOT NULL,
CONSTRAINT fk_project_resume FOREIGN KEY (resume_id) REFERENCES resume(id),
CONSTRAINT fk_project_version FOREIGN KEY (version_id,resume_id) REFERENCES resume_version(id,resume_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_audit_log (
id VARCHAR(36) PRIMARY KEY,
actor_id VARCHAR(36) NOT NULL,
target_id VARCHAR(36) NOT NULL,
action VARCHAR(64) NOT NULL,
created_at TIMESTAMP(6) NOT NULL,
CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES sys_user(id),
CONSTRAINT fk_audit_target FOREIGN KEY (target_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_session_user ON auth_session(user_id,expires_at);
CREATE INDEX idx_resume_user_created ON resume(user_id,created_at,id);
CREATE INDEX idx_file_owner_biz ON file_object(owner_id,biz_type,biz_id);
CREATE INDEX idx_file_storage_status ON file_object(storage_status,created_at);
CREATE INDEX idx_project_resume ON resume_project(resume_id,version_id,sort_no);
CREATE INDEX idx_audit_target ON user_audit_log(target_id,created_at);

