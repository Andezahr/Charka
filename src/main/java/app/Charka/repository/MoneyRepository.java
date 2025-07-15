package app.Charka.repository;

import app.Charka.model.Money;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MoneyRepository extends JpaRepository<Money, Long> {
    List<Money> findByCharacterId(Long characterId);
}