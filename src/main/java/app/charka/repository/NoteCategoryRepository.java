package app.charka.repository;

import app.charka.model.notes.NoteCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NoteCategoryRepository extends JpaRepository<NoteCategory, Long> {

    /** Свои категории персонажа и общие */
    java.util.List<NoteCategory> findByCharacterIdOrCharacterIsNullOrderByName(Long characterId);
}
