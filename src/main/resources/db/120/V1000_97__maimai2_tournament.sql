CREATE TABLE maimai2_tournament
(
    id                INT          NOT NULL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL DEFAULT '',
    enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    ranking_kind      INT          NOT NULL DEFAULT 0,
    score_type        INT          NOT NULL DEFAULT 0,
    notice_start_date VARCHAR(19)  NOT NULL DEFAULT '',
    notice_end_date   VARCHAR(19)  NOT NULL DEFAULT '',
    start_date        VARCHAR(19)  NOT NULL DEFAULT '',
    end_date          VARCHAR(19)  NOT NULL DEFAULT '',
    entry_start_date  VARCHAR(19)  NOT NULL DEFAULT '',
    entry_end_date    VARCHAR(19)  NOT NULL DEFAULT '',
    music_ids         VARCHAR(255) NOT NULL DEFAULT '',
    locked_music_ids  VARCHAR(255) NOT NULL DEFAULT ''
);
