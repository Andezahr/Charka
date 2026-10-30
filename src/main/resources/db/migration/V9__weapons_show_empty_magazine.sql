-- Оружие, созданное до I-03, не знало о заряде: явный ноль показывает на листе, что магазин пуст
INSERT INTO item_property (item_id, prop_key, prop_value)
SELECT shots.item_id, 'ammo', '0'
FROM item_property shots
JOIN item ON item.id = shots.item_id
WHERE shots.prop_key = 'shots'
  AND item.item_type = 'CP2020_WEAPON'
  AND NOT EXISTS (SELECT 1 FROM item_property loaded
                  WHERE loaded.item_id = shots.item_id AND loaded.prop_key = 'ammo');
