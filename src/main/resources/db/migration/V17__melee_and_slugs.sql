-- Ближний бой (C-06): лезвие у оружия из справочника
INSERT INTO item_property (item_id, prop_key, prop_value)
SELECT id, 'edge', 'BLADE' FROM item
WHERE item_type = 'CP2020_WEAPON' AND name IN ('Knife', 'Sword', 'Axe', 'Naginata', 'Shiriken', 'Switchblade', 'Chainsaw');

INSERT INTO item_property (item_id, prop_key, prop_value)
SELECT id, 'edge', 'MONO' FROM item
WHERE item_type = 'CP2020_WEAPON' AND name IN ('Kendachi Monoknife', 'Kendachi MonoKatana');

-- Отметка √ в таблице брони (с. 99): клинок считает её SP за половину
INSERT INTO item_property (item_id, prop_key, prop_value)
SELECT id, 'edgeWeak', '1' FROM item
WHERE item_type = 'CP2020_ARMOR' AND name IN ('Одежда, кожа', 'Кевларовая футболка, жилет', 'Лёгкая бронекуртка', 'Бронежилет Flak', 'Брюки Flak');

-- Калибр дробовика нужен для пуль (I-11)
INSERT INTO item_property (item_id, prop_key, prop_value)
SELECT id, 'gauge', '12' FROM item
WHERE item_type = 'CP2020_WEAPON' AND name IN ('Arasaka Rapid Assault 12', 'CAWS');

INSERT INTO item_property (item_id, prop_key, prop_value)
SELECT id, 'gauge', '10' FROM item
WHERE item_type = 'CP2020_WEAPON' AND name = 'Sternmeyer Stakeout 10';
