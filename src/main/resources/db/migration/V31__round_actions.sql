-- A-05: сколько действий участник сделал в этом раунде боя; штраф следующему считает система, новый раунд обнуляет
ALTER TABLE encounter_participant ADD COLUMN actions INT DEFAULT 0 NOT NULL;
