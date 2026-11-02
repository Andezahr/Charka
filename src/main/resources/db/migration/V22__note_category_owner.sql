-- U-05: категории заметок принадлежат персонажу. Категорию, которой пользуется один персонаж, отдаём ему;
-- общие для нескольких и пустые остаются общими (character_id IS NULL) и видны всем, как раньше.
ALTER TABLE note_category ADD COLUMN character_id BIGINT REFERENCES character (id) ON DELETE CASCADE;

UPDATE note_category
SET character_id = (SELECT MIN(n.character_id)
                    FROM note n
                             JOIN note_category_link l ON l.note_id = n.id
                    WHERE l.category_id = note_category.id)
WHERE (SELECT COUNT(DISTINCT n.character_id)
       FROM note n
                JOIN note_category_link l ON l.note_id = n.id
       WHERE l.category_id = note_category.id) = 1;
