-- M-22: на каком уровне многоуровневой карты стоит фишка; 0 — нижний (у старых боёв)
ALTER TABLE encounter_participant ADD COLUMN pos_level INT DEFAULT 0 NOT NULL;
