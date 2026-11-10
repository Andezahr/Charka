-- A-09: скрытый бросок мастера — игрокам в ленте не виден, мастер может показать всем
ALTER TABLE roll_log ADD COLUMN hidden BOOLEAN DEFAULT FALSE NOT NULL;
