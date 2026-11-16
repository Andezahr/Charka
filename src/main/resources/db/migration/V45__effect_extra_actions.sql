-- KZ-STEEL-JOE: эффект, который в бою можно потратить на ещё одно действие хода (энергетик «Стального Джо»)
ALTER TABLE timed_effect ADD COLUMN extra_actions INT DEFAULT 0 NOT NULL;
