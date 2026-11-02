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

    /** Подложка до 5 МБ */
    public static final int BACKGROUND_LIMIT = 5 * 1024 * 1024;
    private static final java.util.Set<String> IMAGE_TYPES = java.util.Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    /**
     * Положить подложку. Если её ещё не размещали — растянуть по ширине карты, высота по пропорциям картинки.
     *
     * @throws IllegalArgumentException если не картинка или больше 5 МБ
     */
    @Transactional
    public BattleMap setBackground(Long campaignId, Long mapId, byte[] data, String type) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Выберите картинку");
        }
        if (data.length > BACKGROUND_LIMIT) {
            throw new IllegalArgumentException("Картинка больше 5 МБ");
        }
        if (type == null || !IMAGE_TYPES.contains(type)) {
            throw new IllegalArgumentException("Нужна картинка PNG, JPEG, WebP или GIF");
        }
        BattleMap map = get(campaignId, mapId);
        MapDoc doc = MapDoc.parse(map.getDoc());
        double height = doc.h();
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data));
            if (image != null && image.getWidth() > 0) {
                height = (double) doc.w() * image.getHeight() / image.getWidth();
            }
        } catch (java.io.IOException e) {
            // WebP ImageIO не читает: высота по карте, калибровка поправит
        }
        String ver = Integer.toHexString(java.util.Arrays.hashCode(data));
        MapDoc.Background old = doc.bg();
        MapDoc.Background placed = old == null ? new MapDoc.Background(0, 0, doc.w(), height, 0.8, ver)
                : new MapDoc.Background(old.x(), old.y(), old.w(), old.h(), old.opacity(), ver);
        map.setBackground(data);
        map.setBackgroundType(type);
        map.setDoc(doc.withBackground(placed).toJson());
        return map;
    }

    @Transactional
    public void removeBackground(Long campaignId, Long mapId) {
        BattleMap map = get(campaignId, mapId);
        map.setBackground(null);
        map.setBackgroundType(null);
        map.setDoc(MapDoc.parse(map.getDoc()).withBackground(null).toJson());
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
