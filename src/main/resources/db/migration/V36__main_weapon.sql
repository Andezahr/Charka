-- R-07: основное оружие персонажа на листе; null — первое оружие инвентаря
ALTER TABLE character ADD COLUMN main_weapon_id BIGINT;
