package app.charka.map;

import app.charka.exception.NotFoundException;
import app.charka.model.BattleMap;
import app.charka.model.Campaign;
import app.charka.repository.BattleMapRepository;
import app.charka.repository.CampaignRepository;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Карты боя кампании: заготовки мастера.
 */
@Service
@RequiredArgsConstructor
public class BattleMapService {

    public static final int DEFAULT_WIDTH = 30;
    public static final int DEFAULT_HEIGHT = 20;
    private static final int NAME_LENGTH = 100;

    private final BattleMapRepository battleMapRepository;
    private final CampaignRepository campaignRepository;
    private final GameSystemRegistry systemRegistry;

    @Transactional(readOnly = true)
    public List<BattleMap> list(Long campaignId) {
        return battleMapRepository.findByCampaignIdOrderByNameAsc(campaignId);
    }

    /**
     * @throws NotFoundException если карты нет или она другой кампании
     */
    @Transactional(readOnly = true)
    public BattleMap get(Long campaignId, Long mapId) {
        return battleMapRepository.findById(mapId)
                .filter(map -> map.getCampaign().getId().equals(campaignId))
                .orElseThrow(NotFoundException.forId("BattleMap", mapId));
    }

    /** Правила карты системы кампании */
    @Transactional(readOnly = true)
    public MapRules rules(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElseThrow(NotFoundException.forId("Campaign", campaignId));
        return systemRegistry.get(campaign.getSystem()).getMapRules();
    }

    /**
     * Пустая карта с клеткой системы.
     *
     * @throws IllegalArgumentException если название пусто или размер вне пределов
     */
    @Transactional
    public BattleMap create(Long campaignId, String name, int width, int height) {
        MapDoc doc = MapDoc.empty(width, height, rules(campaignId).cellMeters());
        doc.validate();
        return save(campaignId, name, doc);
    }

    /**
     * Новая карта из готового документа: генератор, «Сохранить как новую карту» из боя.
     */
    @Transactional
    public BattleMap save(Long campaignId, String name, MapDoc doc) {
        doc.validate();
        BattleMap map = new BattleMap();
        map.setName(name(name));
        map.setDoc(doc.toJson());
        map.setCampaign(campaignRepository.findById(campaignId).orElseThrow(NotFoundException.forId("Campaign", campaignId)));
        return battleMapRepository.save(map);
    }

    @Transactional
    public BattleMap copy(Long campaignId, Long mapId) {
        BattleMap source = get(campaignId, mapId);
        return save(campaignId, source.getName() + " (копия)", MapDoc.parse(source.getDoc()));
    }

    @Transactional
    public BattleMap rename(Long campaignId, Long mapId, String name) {
        BattleMap map = get(campaignId, mapId);
        map.setName(name(name));
        return map;
    }

    /**
     * Автосохранение редактора: документ целиком.
     *
     * @throws IllegalArgumentException если документ не карта
     */
    @Transactional
    public BattleMap updateDoc(Long campaignId, Long mapId, String json) {
        BattleMap map = get(campaignId, mapId);
        map.setDoc(MapDoc.parse(json).toJson());
        return map;
    }

    @Transactional
    public void delete(Long campaignId, Long mapId) {
        BattleMap map = get(campaignId, mapId);
        map.getCampaign().getMaps().remove(map);
        battleMapRepository.delete(map);
    }

    private static String name(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Назовите карту");
        }
        String stripped = name.strip();
        return stripped.length() > NAME_LENGTH ? stripped.substring(0, NAME_LENGTH) : stripped;
    }
}
