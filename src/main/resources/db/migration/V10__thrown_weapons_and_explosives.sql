-- Метание и взрывчатка (I-09): у предметов из справочника появились новые поля
INSERT INTO item_property (item_id, prop_key, prop_value)
SELECT id, 'thrown', '1' FROM item
WHERE item_type = 'CP2020_WEAPON' AND name IN ('Grenade', 'C-6 Plastic Explosive', 'Knife', 'Shiriken');

INSERT INTO item_property (item_id, prop_key, prop_value)
SELECT id, 'planted', '1' FROM item
WHERE item_type = 'CP2020_WEAPON' AND name IN ('Grenade', 'C-6 Plastic Explosive', 'Mine (all types)');

INSERT INTO item_property (item_id, prop_key, prop_value)
SELECT id, 'area', '5' FROM item
WHERE item_type = 'CP2020_WEAPON' AND name IN ('Grenade', 'C-6 Plastic Explosive');

INSERT INTO item_property (item_id, prop_key, prop_value)
SELECT id, 'area', '2' FROM item
WHERE item_type = 'CP2020_WEAPON' AND name = 'Mine (all types)';

INSERT INTO item_property (item_id, prop_key, prop_value)
SELECT id, 'explosive', '1' FROM item
WHERE item_type = 'CP2020_WEAPON' AND name = 'C-6 Plastic Explosive';

-- Был текст «8D10 за кг», который нельзя бросить
UPDATE item_property SET prop_value = '8D10'
WHERE prop_key = 'damage' AND prop_value = '8D10 за кг';
