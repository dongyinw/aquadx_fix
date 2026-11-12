-- Audit log of the self-service "claim" tools (manual score re-submission and MaiMile edits).
CREATE TABLE maimai2_user_claim_log
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    kind       VARCHAR(16)  NOT NULL,
    music_id   INT          NOT NULL DEFAULT 0,
    level      INT          NOT NULL DEFAULT 0,
    old_value  INT          NOT NULL DEFAULT 0,
    new_value  INT          NOT NULL DEFAULT 0,
    detail     VARCHAR(255) NOT NULL DEFAULT '',
    created_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_maimai2_claim_user_time (user_id, kind, created_at),
    KEY idx_maimai2_claim_time (created_at),
    CONSTRAINT fk_maimai2_claim_user FOREIGN KEY (user_id)
        REFERENCES maimai2_user_detail (id) ON DELETE CASCADE
);
