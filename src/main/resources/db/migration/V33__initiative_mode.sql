-- A-13: инициатива каждый раунд или раз за бой — настройка кампании. Автор решил: CP2020 — каждый раунд (с. 97)
ALTER TABLE campaign ADD COLUMN initiative_each_round BOOLEAN DEFAULT FALSE NOT NULL;
UPDATE campaign SET initiative_each_round = TRUE WHERE game_system = 'CP2020';
