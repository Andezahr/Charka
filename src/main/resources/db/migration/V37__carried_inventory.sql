-- R-08: носимый инвентарь — на листе («На теле», «В сумке»); неносимый («В машине», «В нычке») — только в «Снаряжении»
ALTER TABLE inventory ADD COLUMN carried BOOLEAN DEFAULT TRUE NOT NULL;
