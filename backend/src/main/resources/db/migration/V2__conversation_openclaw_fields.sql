-- OpenClaw 会话关联字段
ALTER TABLE conversations ADD COLUMN openclaw_session_key VARCHAR(255);
ALTER TABLE conversations ADD COLUMN last_message_preview VARCHAR(2000);
ALTER TABLE conversations ADD COLUMN message_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_conversations_openclaw_key ON conversations(openclaw_session_key);
