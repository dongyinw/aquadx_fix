-- Game name review: players submit a new in-game name, an admin approves it (or it is approved automatically later).
CREATE TABLE game_name_request
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    au_id          BIGINT       NOT NULL,
    game           VARCHAR(8)   NOT NULL,
    old_name       VARCHAR(64)  NOT NULL DEFAULT '',
    new_name       VARCHAR(64)  NOT NULL,
    status         VARCHAR(12)  NOT NULL DEFAULT 'PENDING',
    submitted_at   BIGINT       NOT NULL,
    reviewed_at    BIGINT       NULL,
    reviewer_au_id BIGINT       NULL,
    auto_approved  BOOLEAN      NOT NULL DEFAULT FALSE,
    reject_reason  VARCHAR(255) NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    KEY idx_game_name_request_user (au_id, game, status),
    KEY idx_game_name_request_status (status, submitted_at)
);

-- The single super admin, who alone manages the other admins. There is deliberately no API that sets this flag.
ALTER TABLE aqua_net_user
    ADD COLUMN is_super_admin BOOLEAN NOT NULL DEFAULT FALSE;

-- The oldest existing admin account becomes the super admin.
UPDATE aqua_net_user
SET is_super_admin = TRUE
WHERE au_id = (SELECT m.id FROM (SELECT MIN(au_id) AS id FROM aqua_net_user WHERE is_admin = TRUE) m);
