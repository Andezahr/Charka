-- M-16: туман для игроков — игрок видит только фишки, которые видят его персонажи. Мастер выключает в бою
ALTER TABLE campaign ADD COLUMN fog_of_war BOOLEAN DEFAULT TRUE NOT NULL;
