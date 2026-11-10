-- R-09a: «Частые навыки» на листе — навыки, закреплённые звёздочкой на странице навыков (код или custom:<id>)
CREATE TABLE character_pinned_skill (
    character_id BIGINT NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    skill_key    VARCHAR(80) NOT NULL,
    PRIMARY KEY (character_id, skill_key)
);
