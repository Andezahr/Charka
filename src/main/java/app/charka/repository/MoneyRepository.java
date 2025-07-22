package app.charka.repository;

import app.charka.model.Money;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MoneyRepository extends JpaRepository<Money, Long> {
    List<Money> findByCharacterId(Long characterId);
}