-- Announcements shown to players on the website, managed in the admin console.
CREATE TABLE net_announcement
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    title      VARCHAR(100) NOT NULL DEFAULT '',
    content    TEXT         NOT NULL,
    level      VARCHAR(16)  NOT NULL DEFAULT 'info',
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    start_date VARCHAR(19)  NOT NULL DEFAULT '',
    end_date   VARCHAR(19)  NOT NULL DEFAULT '',
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    PRIMARY KEY (id)
);
