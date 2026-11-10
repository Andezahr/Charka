package app.charka.encounter;

import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Encounter;
import app.charka.model.Scene;
import app.charka.model.SceneNpc;
import app.charka.npc.NpcService;
import app.charka.repository.CharacterRepository;
import app.charka.repository.SceneRepository;
import app.charka.service.campaign.CampaignService;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Заготовки боя (A-06). Мастер до игры собирает сцену: карта (где встают игроки и NPC — её зоны старта), NPC по шаблону
 * или существующие, кто в засаде. «Начать сцену» — одно нажатие: NPC создаются, бой начинается, фишки встают, инициатива
 * бросается, засада ждёт. Сцена после боя остаётся: сыграть заново — новые NPC по тем же строкам.
 */
@Service
@RequiredArgsConstructor
public class SceneService {

    private static final int NAME_LENGTH = 100;

    private final SceneRepository sceneRepository;
    private final CampaignService campaignService;
    private final CharacterRepository characterRepository;
    private final NpcService npcService;
    private final EncounterService encounterService;
    private final GameSystemRegistry systemRegistry;

    @Transactional(readOnly = true)
    public List<Scene> list(Long campaignId) {
        List<Scene> scenes = sceneRepository.findByCampaignIdOrderByNameAsc(campaignId);
        scenes.forEach(scene -> scene.getNpcs().size());
        return scenes;
    }

    /** @throws java.util.NoSuchElementException (404), если сцены нет в этой кампании */
    @Transactional(readOnly = true)
    public Scene get(Long campaignId, Long sceneId) {
        return sceneRepository.findById(sceneId)
                .filter(scene -> scene.getCampaign().getId().equals(campaignId))
                .orElseThrow(NotFoundException.forId("Scene", sceneId));
    }

    /**
     * @param battleMapId карта кампании; null — без карты
     * @throws IllegalArgumentException пустое или длинное название, чужая карта
     */
    @Transactional
    public Scene create(Long campaignId, String name, Long battleMapId) {
        Campaign campaign = campaignService.getById(campaignId);
        Scene scene = new Scene();
        scene.setCampaign(campaign);
        scene.setName(checkName(name));
        scene.setBattleMapId(checkMap(campaign, battleMapId));
        return sceneRepository.save(scene);
    }

    @Transactional
    public Scene update(Long campaignId, Long sceneId, String name, Long battleMapId, boolean withPlayers, boolean groupPlayers, boolean groupNpcs) {
        Scene scene = get(campaignId, sceneId);
        scene.setName(checkName(name));
        scene.setBattleMapId(checkMap(scene.getCampaign(), battleMapId));
        scene.setWithPlayers(withPlayers);
        scene.setGroupPlayers(groupPlayers);
        scene.setGroupNpcs(groupNpcs);
        return scene;
    }

    @Transactional
    public void delete(Long campaignId, Long sceneId) {
        sceneRepository.delete(get(campaignId, sceneId));
    }

    /**
     * NPC в сцену: {@code who} — «t:КОД» (шаблон системы, «t:» — пустой NPC) или «c:id» (персонаж кампании).
     *
     * @throws IllegalArgumentException неизвестный шаблон, персонаж из другой кампании, неверное число
     */
    @Transactional
    public void addNpc(Long campaignId, Long sceneId, String who, String name, int count, boolean advanced, boolean ambush) {
        Scene scene = get(campaignId, sceneId);
        SceneNpc npc = new SceneNpc();
        npc.setScene(scene);
        if (who != null && who.startsWith("c:")) {
            Long id = Long.valueOf(who.substring(2));
            Character character = characterRepository.findById(id)
                    .filter(found -> found.getCampaign() != null && found.getCampaign().getId().equals(campaignId))
                    .orElseThrow(() -> new IllegalArgumentException("Такого персонажа в кампании нет"));
            npc.setCharacter(character);
            npc.setCount(1);
        } else {
            String code = who == null || !who.startsWith("t:") ? "" : who.substring(2);
            GameSystem system = systemRegistry.get(scene.getCampaign().getSystem());
            if (!code.isEmpty() && system.getNpcTemplates().stream().noneMatch(template -> template.code().equals(code))) {
                throw new IllegalArgumentException("Нет такого шаблона: " + code);
            }
            if (count < 1 || count > 20) {
                throw new IllegalArgumentException("Сколько: от 1 до 20");
            }
            npc.setTemplateCode(code.isEmpty() ? null : code);
            npc.setName(name == null || name.isBlank() ? null : name.strip());
            npc.setCount(count);
            npc.setAdvanced(advanced);
        }
        npc.setAmbush(ambush);
        scene.getNpcs().add(npc);
    }

    @Transactional
    public void removeNpc(Long campaignId, Long sceneId, Long npcId) {
        get(campaignId, sceneId).getNpcs().removeIf(npc -> npc.getId().equals(npcId));
    }

    /** Засада у строки сцены: включить или выключить одним нажатием */
    @Transactional
    public void toggleAmbush(Long campaignId, Long sceneId, Long npcId) {
        get(campaignId, sceneId).getNpcs().stream().filter(npc -> npc.getId().equals(npcId))
                .forEach(npc -> npc.setAmbush(!npc.isAmbush()));
    }

    /**
     * Начать бой по сцене: игроки (если отмечено, кроме выбывших), NPC сцены — новые по шаблону и существующие,
     * карта сцены; засада ждёт.
     *
     * @throws IllegalArgumentException бой уже идёт или некого вводить
     */
    @Transactional
    public Encounter start(Long campaignId, Long sceneId) {
        Scene scene = get(campaignId, sceneId);
        Campaign campaign = scene.getCampaign();
        GameSystem system = systemRegistry.get(campaign.getSystem());
        if (encounterService.activeRound(campaignId) != null) {
            throw new IllegalArgumentException("Бой уже идёт");
        }
        List<Long> fighters = new ArrayList<>();
        Set<Long> ambush = new HashSet<>();
        if (scene.isWithPlayers()) {
            campaign.getCharacters().stream()
                    .filter(character -> !character.isNpc() && !system.getCombatCard(character).out())
                    .forEach(character -> fighters.add(character.getId()));
        }
        for (SceneNpc row : scene.getNpcs()) {
            List<Character> characters = row.getCharacter() != null
                    ? (system.getCombatCard(row.getCharacter()).out() ? List.of() : List.of(row.getCharacter()))
                    : npcService.create(campaignId, row.getTemplateCode(), row.getName(), row.getCount(), row.isAdvanced());
            characters.forEach(character -> {
                fighters.add(character.getId());
                if (row.isAmbush()) {
                    ambush.add(character.getId());
                }
            });
        }
        if (fighters.isEmpty()) {
            throw new IllegalArgumentException("В сцене некого вводить в бой");
        }
        Encounter encounter = encounterService.start(campaignId, fighters, scene.isGroupPlayers(), scene.isGroupNpcs(), scene.getBattleMapId());
        encounter.getParticipants().stream()
                .filter(participant -> ambush.contains(participant.getCharacter().getId()))
                .forEach(participant -> participant.setWaiting(true));
        return encounter;
    }

    private static String checkName(String name) {
        String stripped = name == null ? "" : name.strip();
        if (stripped.isEmpty() || stripped.length() > NAME_LENGTH) {
            throw new IllegalArgumentException("Название сцены — от 1 до " + NAME_LENGTH + " символов");
        }
        return stripped;
    }

    private static Long checkMap(Campaign campaign, Long battleMapId) {
        if (battleMapId == null) {
            return null;
        }
        if (campaign.getMaps().stream().noneMatch(map -> map.getId().equals(battleMapId))) {
            throw new IllegalArgumentException("Такой карты в кампании нет");
        }
        return battleMapId;
    }
}
