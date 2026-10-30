-- Тип предмета и его свойства: какие поля есть у типа, решает система кампании (I-01)
ALTER TABLE item ADD COLUMN item_type VARCHAR(64) DEFAULT 'GENERIC' NOT NULL;

CREATE TABLE item_property
(
    item_id    BIGINT       NOT NULL REFERENCES item (id),
    prop_key   VARCHAR(64)  NOT NULL,
    prop_value VARCHAR(255) NOT NULL,
    PRIMARY KEY (item_id, prop_key)
);
