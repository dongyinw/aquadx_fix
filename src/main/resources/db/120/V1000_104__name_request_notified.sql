-- Whether the player has been shown the outcome of a reviewed name request on the home page.
ALTER TABLE game_name_request
    ADD COLUMN notified BOOLEAN NOT NULL DEFAULT FALSE;
