-- K-02: текстовое состояние счётчика сверх чисел (Космозэки: набор зон тела — названия, цифры кубика, множители)
CREATE TABLE counter_text
(
    counter_id BIGINT        NOT NULL REFERENCES counter (id),
    text_key   VARCHAR(64)   NOT NULL,
    text_value VARCHAR(4000),
    PRIMARY KEY (counter_id, text_key)
);

-- K-11: поля листа, которые объявляет система (Космозэки: теги, приговор, качества)
CREATE TABLE character_profile
(
    character_id  BIGINT        NOT NULL REFERENCES character (id),
    profile_key   VARCHAR(64)   NOT NULL,
    profile_value VARCHAR(4000),
    PRIMARY KEY (character_id, profile_key)
);
