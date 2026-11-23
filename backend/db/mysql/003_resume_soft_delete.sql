-- Incremental migration for existing databases. Execute once after reviewing schema state.
-- Does not delete rows or files. Existing confirmed versions remain intact.
ALTER TABLE resume ADD COLUMN deleted_at TIMESTAMP(6) NULL;
CREATE INDEX idx_resume_user_active ON resume(user_id,deleted_at,created_at,id);
