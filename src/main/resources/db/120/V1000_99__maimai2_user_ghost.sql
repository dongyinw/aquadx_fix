-- Ghost data uploaded by cabinets (UpsertUserAll.userGhost). Other players see these as VS ghosts.
CREATE TABLE maimai2_user_ghost
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    music_id       INT          NOT NULL,
    difficulty     INT          NOT NULL,
    name           VARCHAR(255) NOT NULL DEFAULT '',
    icon_id        INT          NOT NULL DEFAULT 0,
    plate_id       INT          NOT NULL DEFAULT 0,
    title_id       INT          NOT NULL DEFAULT 0,
    rate           INT          NOT NULL DEFAULT 0,
    udemae_rate    INT          NOT NULL DEFAULT 0,
    course_rank    INT          NOT NULL DEFAULT 0,
    class_rank     INT          NOT NULL DEFAULT 0,
    class_value    INT          NOT NULL DEFAULT 0,
    play_datetime  VARCHAR(32)  NOT NULL DEFAULT '',
    shop_id        INT          NOT NULL DEFAULT 0,
    region_code    INT          NOT NULL DEFAULT 0,
    type_id        INT          NOT NULL DEFAULT 0,
    version        INT          NOT NULL DEFAULT 0,
    result_bit_list TEXT        NOT NULL,
    result_num     INT          NOT NULL DEFAULT 0,
    achievement    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_maimai2_user_ghost (user_id, music_id, difficulty),
    KEY idx_maimai2_user_ghost_recent (id),
    CONSTRAINT fk_maimai2_user_ghost_user FOREIGN KEY (user_id)
        REFERENCES maimai2_user_detail (id) ON DELETE CASCADE
);

CREATE INDEX idx_maimai2_user_friend_season_ranking_season
    ON maimai2_user_friend_season_ranking (season_id, point);
