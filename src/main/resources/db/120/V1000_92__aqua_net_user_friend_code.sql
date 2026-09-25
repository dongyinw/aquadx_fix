ALTER TABLE aqua_net_user
    ADD COLUMN friend_code VARCHAR(16) NULL;

UPDATE aqua_net_user
SET friend_code = UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 16))
WHERE friend_code IS NULL;

CREATE UNIQUE INDEX uk_aqua_net_user_friend_code ON aqua_net_user (friend_code);
