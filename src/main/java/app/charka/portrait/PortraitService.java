package app.charka.portrait;

import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.CharacterPortrait;
import app.charka.repository.CharacterPortraitRepository;
import app.charka.repository.CharacterRepository;
import app.charka.repository.EncounterRepository;
import app.charka.map.BattleFieldService;
import app.charka.security.AccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PH-20 [CORE]: портрет персонажа — загрузка, выдача, адрес с версией для шаблонов и карты.
 * Картинку обрезает и сжимает браузер; сервер проверяет только тип и размер.
 */
@Service
@RequiredArgsConstructor
public class PortraitService {

    /** Браузер сжимает до 512 px и 300 КБ; больше — значит пришло мимо обрезки */
    public static final int MAX_BYTES = 300 * 1024;
    public static final Set<String> TYPES = Set.of("image/webp", "image/jpeg", "image/png");

    private final CharacterPortraitRepository portraitRepository;
    private final CharacterRepository characterRepository;
    private final EncounterRepository encounterRepository;
    private final AccessService accessService;
    private final BattleFieldService battleFieldService;

    /**
     * Загрузить или заменить портрет.
     *
     * @throws IllegalArgumentException пусто, не картинка или больше {@link #MAX_BYTES}
     */
    @Transactional
    public CharacterPortrait set(Long characterId, byte[] data, String type) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Выберите картинку");
        }
        if (type == null || !TYPES.contains(type)) {
            throw new IllegalArgumentException("Портрет — картинка WebP, JPEG или PNG");
        }
        if (data.length > MAX_BYTES) {
            throw new IllegalArgumentException("Портрет больше 300 КБ: загрузите со страницы листа — браузер сам уменьшит картинку");
        }
        characterRepository.findById(characterId).orElseThrow(NotFoundException.forId("Character", characterId));
        CharacterPortrait portrait = portraitRepository.findById(characterId).orElseGet(CharacterPortrait::new);
        portrait.setCharacterId(characterId);
        portrait.setData(data);
        portrait.setContentType(type);
        portrait.setUpdatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        return portraitRepository.save(portrait);
    }

    @Transactional
    public void remove(Long characterId) {
        portraitRepository.findById(characterId).ifPresent(portraitRepository::delete);
    }

    @Transactional(readOnly = true)
    public Optional<CharacterPortrait> find(Long characterId) {
        return portraitRepository.findById(characterId);
    }

    /**
     * Портрет для того, кто смотрит, из кампании {@code campaignId}: мастеру — любой, игроку — персонажи игроков
     * и NPC идущего боя, которых его персонажи видят (туман войны, M-16).
     */
    @Transactional(readOnly = true)
    public Optional<CharacterPortrait> forViewer(Long campaignId, Long characterId) {
        Character character = characterRepository.findById(characterId)
                .filter(found -> found.getCampaign() != null && found.getCampaign().getId().equals(campaignId))
                .orElse(null);
        if (character == null || !visible(character)) {
            return Optional.empty();
        }
        return portraitRepository.findById(characterId);
    }

    private boolean visible(Character character) {
        Campaign campaign = character.getCampaign();
        if (!accessService.isEnabled() || accessService.isGm(campaign) || !character.isNpc() || accessService.canControl(character)) {
            return true;
        }
        Set<Long> mine = campaign.getCharacters().stream().filter(accessService::canControl)
                .map(Character::getId).collect(Collectors.toSet());
        Set<Long> hidden = battleFieldService.hiddenFor(campaign.getId(), new BattleFieldService.Viewer(mine));
        return encounterRepository.findByCampaignId(campaign.getId()).stream()
                .flatMap(encounter -> encounter.getParticipants().stream())
                .anyMatch(p -> p.getCharacter().getId().equals(character.getId()) && !hidden.contains(p.getId()));
    }
}
