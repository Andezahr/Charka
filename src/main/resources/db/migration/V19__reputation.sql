-- G-03: репутация у всех уже созданных персонажей CP2020, новым счётчик создаётся вместе с персонажем.
INSERT INTO counter (name, counter_value, type, character_id)
SELECT 'Репутация', 0, 'CP2020_REP', c.id
FROM character c
         JOIN campaign ca ON c.campaign_id = ca.id
WHERE ca.game_system = 'CP2020';
