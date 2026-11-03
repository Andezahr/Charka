package app.charka.security;

import app.charka.model.AppUser;
import app.charka.repository.AppUserRepository;
import app.charka.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Пользователи: регистрация и вход.
 */
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_.-]{3,32}$");
    private static final int MIN_PASSWORD = 6;

    private final AppUserRepository userRepository;
    private final CampaignRepository campaignRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        AppUser user = userRepository.findByUsername(normalize(username))
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return User.withUsername(user.getUsername()).password(user.getPasswordHash()).roles("USER").build();
    }

    /**
     * Первый зарегистрированный становится мастером всех кампаний, у которых мастера нет: так данные до U-03 не теряются.
     *
     * @throws IllegalArgumentException если логин занят или не подходит, пароль короткий
     */
    @Transactional
    public AppUser register(String username, String password) {
        String login = normalize(username);
        if (!USERNAME.matcher(login).matches()) {
            throw new IllegalArgumentException("Логин — от 3 до 32 латинских букв, цифр, точек, дефисов и подчёркиваний");
        }
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException("Пароль — от " + MIN_PASSWORD + " символов");
        }
        if (userRepository.findByUsername(login).isPresent()) {
            throw new IllegalArgumentException("Логин занят");
        }
        boolean first = userRepository.count() == 0;
        AppUser user = new AppUser();
        user.setUsername(login);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setCreatedAt(LocalDateTime.now());
        AppUser saved = userRepository.save(user);
        if (first) {
            campaignRepository.findAll().stream().filter(campaign -> campaign.getGm() == null).forEach(campaign -> campaign.setGm(saved));
        }
        return saved;
    }

    static String normalize(String username) {
        return username == null ? "" : username.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
