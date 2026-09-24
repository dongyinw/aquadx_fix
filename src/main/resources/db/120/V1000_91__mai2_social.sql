CREATE TABLE mai2_social_friend (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_low_id BIGINT NOT NULL,
    user_high_id BIGINT NOT NULL,
    friend_status VARCHAR(16) NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mai2_social_pair (user_low_id, user_high_id),
    KEY idx_mai2_social_high_status (user_high_id, friend_status),
    KEY idx_mai2_social_low_status (user_low_id, friend_status),
    CONSTRAINT fk_mai2_social_user_low FOREIGN KEY (user_low_id)
        REFERENCES maimai2_user_detail (id) ON DELETE CASCADE,
    CONSTRAINT fk_mai2_social_user_high FOREIGN KEY (user_high_id)
        REFERENCES maimai2_user_detail (id) ON DELETE CASCADE,
    CONSTRAINT fk_mai2_social_requested_by FOREIGN KEY (requested_by_user_id)
        REFERENCES maimai2_user_detail (id) ON DELETE CASCADE
);
