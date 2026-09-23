CREATE TABLE mai2_circle (
    id BIGINT NOT NULL AUTO_INCREMENT,
    circle_name VARCHAR(32) NOT NULL,
    circle_code VARCHAR(16) NOT NULL,
    owner_ext_id BIGINT NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    allow_anyone_join BOOLEAN NOT NULL DEFAULT FALSE,
    circle_comment VARCHAR(500) NOT NULL DEFAULT '',
    circle_class INT NOT NULL DEFAULT 0,
    is_place BOOLEAN NOT NULL DEFAULT FALSE,
    place_id INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mai2_circle_code (circle_code),
    UNIQUE KEY uk_mai2_circle_owner (owner_ext_id),
    KEY idx_mai2_circle_public_created (is_public, created_at)
);

CREATE TABLE mai2_circle_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    circle_id BIGINT NOT NULL,
    aime_ext_id BIGINT NOT NULL,
    joined_at DATETIME NOT NULL,
    last_login_date VARCHAR(19) NOT NULL DEFAULT '',
    point INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mai2_circle_member_aime (aime_ext_id),
    UNIQUE KEY uk_mai2_circle_member_pair (circle_id, aime_ext_id),
    KEY idx_mai2_circle_member_rank (circle_id, point),
    CONSTRAINT fk_mai2_circle_member_circle
        FOREIGN KEY (circle_id) REFERENCES mai2_circle (id) ON DELETE CASCADE
);

CREATE TABLE mai2_circle_join_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    circle_id BIGINT NOT NULL,
    aime_ext_id BIGINT NOT NULL,
    requested_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mai2_circle_request_aime (aime_ext_id),
    KEY idx_mai2_circle_request_circle (circle_id, requested_at),
    CONSTRAINT fk_mai2_circle_request_circle
        FOREIGN KEY (circle_id) REFERENCES mai2_circle (id) ON DELETE CASCADE
);
