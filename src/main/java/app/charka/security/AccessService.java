package app.charka.security;

import app.charka.model.AppUser;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.repository.AppUserRepository;
import app.charka.repository.CharacterRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Кто что видит (U-03). Мастер кампании — всё в ней; игрок — своих персонажей, а в кампании — её страницу, бой, карту и чат.
 * Без пользователей ({@code charka.security.enabled=false}) можно всё.
 */
@Service
public class AccessService {

    private final AppUserRepository userRepository;
    private final CharacterRepository characterRepository;
    private final boolean enabled;

    public AccessService(AppUserRepository userRepository, CharacterRepository characterRepository,
                         @Value("${charka.security.enabled:true}") boolean enabled) {
        this.userRepository = userRepository;
        this.characterRepository = characterRepository;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Кто вошёл; пусто — никто или пользователей нет */
    @Transactional(readOnly = true)
    public Optional<AppUser> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!enabled || auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return userRepository.findByUsername(auth.getName());
    }

    public boolean isGm(Campaign campaign) {
        if (!enabled) {
            return true;
        }
        Long me = current().map(AppUser::getId).orElse(null);
        return me != null && campaign != null && campaign.getGm() != null && me.equals(campaign.getGm().getId());
    }

    /** Есть свой персонаж в кампании. Запросом, а не по {@code campaign.getCharacters()}: кампания из списка в шаблоне — без сессии */
    @Transactional(readOnly = true)
    public boolean isPlayer(Campaign campaign) {
        Long me = current().map(AppUser::getId).orElse(null);
        return me != null && campaign != null && campaign.getId() != null
                && characterRepository.existsByCampaignIdAndPlayerId(campaign.getId(), me);
    }

    public boolean canOpen(Campaign campaign) {
        return isGm(campaign) || isPlayer(campaign);
    }

    /** Лист, действия и атаки персонажа: его игрок или мастер кампании. Персонаж без кампании — всем */
    public boolean canControl(Character character) {
        if (!enabled || character.getCampaign() == null) {
            return true;
        }
        return isGm(character.getCampaign()) || owns(character, current().map(AppUser::getId).orElse(null));
    }

    /** Персонажи кампании, которых видно: мастеру все, игроку свои */
    public List<Character> visible(Campaign campaign) {
        if (isGm(campaign)) {
            return campaign.getCharacters();
        }
        Long me = current().map(AppUser::getId).orElse(null);
        return campaign.getCharacters().stream().filter(character -> owns(character, me)).toList();
    }

    private static boolean owns(Character character, Long userId) {
        return userId != null && character.getPlayer() != null && Objects.equals(character.getPlayer().getId(), userId);
    }
}
