package app.charka.repository;

import app.charka.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {
    @Query
    List<Note> findByCharacterIdOrderByCreatedAtDesc(Long characterId);

    @Query("SELECT DISTINCT n FROM Note n LEFT JOIN FETCH n.categories c WHERE n.character.id = :characterId AND c.id = :categoryId")
    List<Note> findByCharacterAndCategoryWithCategories(@Param("characterId") Long characterId,
                                                        @Param("categoryId") Long categoryId);

    // если нет - добавить и этот метод
    @Query("SELECT DISTINCT n FROM Note n LEFT JOIN FETCH n.categories WHERE n.character.id = :characterId")
    List<Note> findByCharacterWithCategories(@Param("characterId") Long characterId);
}

