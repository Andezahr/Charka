package app.charka.service;

import app.charka.model.Wound;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface WoundService {
    @Transactional(readOnly = true)
    List<Wound> getByCharacter(Long characterId);

    @Transactional(readOnly = true)
    Wound getById(Long id);

    @Transactional
    Wound create(Long characterId, Wound wound);

    @Transactional
    Wound update(Long id, Wound updatedWound);

    @Transactional
    void delete(Long id);
}
