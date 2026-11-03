-- Гранатомёт заряжается гранатой из инвентаря (I-10): старый предмет считался заряженным, но заряда в нём нет
INSERT INTO item_property (item_id, prop_key, prop_value)
SELECT id, 'launcher', '1' FROM item WHERE item_type = 'CP2020_WEAPON' AND name = 'Grenade Launcher';

UPDATE item_property SET prop_value = '0'
WHERE prop_key = 'ammo'
  AND item_id IN (SELECT id FROM item WHERE item_type = 'CP2020_WEAPON' AND name = 'Grenade Launcher');
