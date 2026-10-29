-- S-02: игровая система кампании. Все существующие кампании — Cyberpunk 2020.
ALTER TABLE campaign ADD COLUMN game_system VARCHAR(32) DEFAULT 'CP2020' NOT NULL;
