package app.charka.repository;

import app.charka.model.notes.NoteCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NoteCategoryRepository extends JpaRepository<NoteCategory, Long> {
}
