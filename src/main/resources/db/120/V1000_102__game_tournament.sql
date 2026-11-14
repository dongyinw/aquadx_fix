-- Website tournaments of CHUNITHM (chu3) and O.N.G.E.K.I. (ongeki). maimai DX keeps using maimai2_tournament.
CREATE TABLE game_tournament
(
    pk                BIGINT       NOT NULL AUTO_INCREMENT,
    game              VARCHAR(8)   NOT NULL,
    tournament_id     INT          NOT NULL,
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
    locked_music_ids  VARCHAR(255) NOT NULL DEFAULT '',
    PRIMARY KEY (pk),
    UNIQUE KEY uk_game_tournament (game, tournament_id)
);
