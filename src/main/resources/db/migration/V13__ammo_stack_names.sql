-- Запас патронов называется по патрону: «коробка 50» в названии переставала быть правдой после первой перезарядки.
UPDATE item
SET name = 'Патроны ' || (SELECT p.prop_value FROM item_property p WHERE p.item_id = item.id AND p.prop_key = 'caliber') || ' AP'
WHERE item_type = 'CP2020_AMMO' AND name LIKE '%коробка % AP';

UPDATE item
SET name = 'Патроны ' || (SELECT p.prop_value FROM item_property p WHERE p.item_id = item.id AND p.prop_key = 'caliber')
WHERE item_type = 'CP2020_AMMO' AND name LIKE '%коробка %';

UPDATE item
SET description = NULL
WHERE item_type = 'CP2020_AMMO' AND description LIKE 'Цена за коробку%';
