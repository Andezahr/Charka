-- K-10: у персонажа может быть несколько ролей. Текущая роль переносится в новую таблицу.
CREATE TABLE character_role
(
    character_id BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    role_code    VARCHAR(64) NOT NULL,
    PRIMARY KEY (character_id, role_code)
);

INSERT INTO character_role (character_id, role_code)
SELECT id, role_code
FROM character
WHERE role_code IS NOT NULL;

ALTER TABLE character DROP COLUMN role_code;

-- G-05: очки улучшения у всех уже созданных персонажей CP2020, новым счётчик создаётся вместе с персонажем.
INSERT INTO counter (name, counter_value, type, character_id)
SELECT 'Очки улучшения', 0, 'CP2020_IP', c.id
FROM character c
         JOIN campaign ca ON c.campaign_id = ca.id
WHERE ca.game_system = 'CP2020';
