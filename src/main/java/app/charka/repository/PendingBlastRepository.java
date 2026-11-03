package app.charka.repository;

import app.charka.model.PendingBlast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PendingBlastRepository extends JpaRepository<PendingBlast, Long> {
    List<PendingBlast> findByCharacterIdOrderByIdDesc(Long characterId);
}
