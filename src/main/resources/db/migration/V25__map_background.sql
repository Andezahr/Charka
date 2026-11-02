-- M-11: картинка-подложка карты (до 5 МБ); где она лежит на сетке — в документе карты (поле bg)
ALTER TABLE battle_map ADD COLUMN background BYTEA;
ALTER TABLE battle_map ADD COLUMN background_type VARCHAR(32);
