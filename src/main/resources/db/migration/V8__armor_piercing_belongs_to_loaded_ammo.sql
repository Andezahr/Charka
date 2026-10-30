-- Бронебойность теперь свойство заряженных патронов (I-03), а у оружия из V7 магазин ещё пуст
DELETE FROM item_property
WHERE prop_key = 'armorPiercing'
  AND item_id IN (SELECT id FROM item WHERE item_type = 'CP2020_WEAPON');
