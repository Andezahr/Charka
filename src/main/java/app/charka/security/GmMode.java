package app.charka.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;

/**
 * A-15: режим мастера на карте боя, выбранный на этом устройстве (cookie ставит app.js по переключателю).
 * «Бог» — как всегда: любые фишки, без лимита бега, ＋ Фишка, правка карты. «За NPC» — мастер ходит только
 * своими NPC и по правилам бега, как игрок. Игрокам режим ничего не меняет: проверяется только у мастера.
 */
public final class GmMode {

    /** Имя cookie; значение {@value #NPC} — «За NPC», иначе «Бог» */
    public static final String COOKIE = "charka.gmMode";
    public static final String NPC = "npc";

    private GmMode() {
    }

    /** Выбран ли на устройстве режим «За NPC» */
    public static boolean npcOnly(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return cookies != null && Arrays.stream(cookies)
                .anyMatch(cookie -> COOKIE.equals(cookie.getName()) && NPC.equals(cookie.getValue()));
    }
}
