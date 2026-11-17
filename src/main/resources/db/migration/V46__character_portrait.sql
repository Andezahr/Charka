-- PH-20: портрет персонажа. Отдельная таблица, чтобы снимок отмены и Character не таскали байты картинки
CREATE TABLE character_portrait
(
    character_id BIGINT      NOT NULL PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE,
    data         BYTEA       NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    updated_at   TIMESTAMP   NOT NULL
);
