-- M-16: сколько метров участник уже пробежал в этом раунде; бег за раунд — не дальше MA × 3 (с. 98), новый раунд обнуляет
ALTER TABLE encounter_participant ADD COLUMN moved_meters INT DEFAULT 0 NOT NULL;
