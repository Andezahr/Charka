-- Один кубик инициативы на сторону (книга, с. 97) и то, что участник объявил в начале раунда (быстрое выхватывание)
ALTER TABLE encounter ADD COLUMN group_players BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE encounter ADD COLUMN group_npcs BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE encounter_participant ADD COLUMN declaration VARCHAR(64);
