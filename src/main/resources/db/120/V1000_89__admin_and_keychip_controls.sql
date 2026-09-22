ALTER TABLE aqua_net_user
    ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE user_keychip
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE allnet_keychip_sessions
    ADD COLUMN keychip_id VARCHAR(32) NULL;

CREATE INDEX idx_keychip_session_keychip
    ON allnet_keychip_sessions (keychip_id);
