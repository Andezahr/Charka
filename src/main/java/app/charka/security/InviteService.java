package app.charka.security;

import app.charka.exception.NotFoundException;
import app.charka.model.AppUser;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Приглашение игроков (A-02): у кампании ссылка {@code /join/{token}} и QR-код на странице мастера.
 * Игрок открывает её на телефоне, входит или регистрируется и забирает свободного персонажа — мастер ничего не вписывает.
 */
@Service
@RequiredArgsConstructor
public class InviteService {

    /** Сессия: кампании, в которые пригласили, — им можно открыть мастер создания персонажа */
    public static final String INVITED = InviteService.class.getName() + ".invited";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CampaignRepository campaignRepository;
    private final CharacterRepository characterRepository;
    private final AccessService accessService;

    /** Токен приглашения; нет — выдаётся */
    @Transactional
    public String token(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElseThrow(NotFoundException.forId("Campaign", campaignId));
        if (campaign.getInviteToken() == null) {
            campaign.setInviteToken(newToken());
        }
        return campaign.getInviteToken();
    }

    /** Новая ссылка: старая перестаёт работать */
    @Transactional
    public String reset(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElseThrow(NotFoundException.forId("Campaign", campaignId));
        campaign.setInviteToken(newToken());
        return campaign.getInviteToken();
    }

    private static String newToken() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** @throws java.util.NoSuchElementException (404), если ссылку сбросили или её не было */
    @Transactional(readOnly = true)
    public Campaign campaign(String token) {
        return campaignRepository.findByInviteToken(token).orElseThrow(() -> new NotFoundException("Приглашение устарело: попросите у мастера новое"));
    }

    /** Персонажи без игрока, кроме NPC */
    public List<Character> free(Campaign campaign) {
        return campaign.getCharacters().stream().filter(character -> !character.isNpc() && character.getPlayer() == null).toList();
    }

    /**
     * Игрок забирает свободного персонажа.
     *
     * @throws IllegalArgumentException если не вошёл, персонаж чужой, из другой кампании или NPC
     */
    @Transactional
    public Character claim(String token, Long characterId) {
        Campaign campaign = campaign(token);
        AppUser me = accessService.current().orElseThrow(() -> new IllegalArgumentException("Сначала войдите"));
        Character character = characterRepository.findById(characterId)
                .filter(found -> found.getCampaign() != null && found.getCampaign().getId().equals(campaign.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Такого персонажа в кампании нет"));
        if (character.isNpc()) {
            throw new IllegalArgumentException("Это персонаж мастера");
        }
        if (character.getPlayer() != null && !character.getPlayer().getId().equals(me.getId())) {
            throw new IllegalArgumentException("Этого персонажа уже забрал другой игрок");
        }
        character.setPlayer(me);
        return character;
    }

    /**
     * Адрес приглашения с подписью сети: мастер видит, какой выбрать.
     *
     * @param label       «Кабель: Intel(R) Ethernet…», «VPN: Radmin VPN», «Виртуальная сеть Windows»
     * @param recommended обычная сеть (кабель или Wi-Fi): телефон рядом видит её; виртуальные и VPN — нет
     */
    public record Address(String url, String label, boolean recommended) {
    }

    /** Вид сети по описанию адаптера: что из этого видит телефон рядом */
    enum Network {
        LAN("Wi-Fi или кабель", true), VPN("VPN — телефон в ней, только если сам подключён к этому VPN", false),
        VIRTUAL("виртуальная сеть Windows (WSL, Hyper-V) — телефону не видна", false);

        final String note;
        final boolean reachable;

        Network(String note, boolean reachable) {
            this.note = note;
            this.reachable = reachable;
        }

        static Network of(String description) {
            String text = description == null ? "" : description.toLowerCase();
            if (text.matches(".*(hyper-v|vethernet|wsl|virtualbox|vmware|virtual ethernet|default switch|docker).*")) {
                return VIRTUAL;
            }
            if (text.matches(".*(vpn|wireguard|tunnel|tap-|tap |radmin|hamachi|zerotier|tailscale|openvpn|wintun).*")) {
                return VPN;
            }
            return LAN;
        }
    }

    /**
     * Адреса ноутбука, по которым телефон откроет приложение: IPv4 включённых сетевых карт, кроме петли, с подписью сети;
     * обычная сеть (кабель, Wi-Fi) — первой, потом VPN, виртуальные — в конце. Если страницу открыли не с localhost —
     * её адрес первым: он уже работает.
     */
    public List<Address> addresses(String scheme, String requestHost, int port) {
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        java.util.function.Function<String, String> url = host -> scheme + "://" + host + (defaultPort ? "" : ":" + port);
        List<Address> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (requestHost != null && !requestHost.equals("localhost") && !requestHost.startsWith("127.") && !requestHost.equals("[::1]")) {
            result.add(new Address(url.apply(requestHost), "адрес, по которому открыта эта страница", true));
            seen.add(requestHost);
        }
        List<Address> found = new ArrayList<>();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
                    continue;
                }
                Network kind = Network.of(network.getDisplayName());
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress()
                            && seen.add(address.getHostAddress())) {
                        found.add(new Address(url.apply(address.getHostAddress()),
                                kind.note + " · " + network.getDisplayName(), kind.reachable && address.isSiteLocalAddress()));
                    }
                }
            }
        } catch (SocketException e) {
            // сетевых карт не прочитать — остаётся адрес страницы
        }
        found.sort(java.util.Comparator.comparing((Address address) -> !address.recommended())
                .thenComparing(address -> address.label().startsWith(Network.VIRTUAL.note)));
        result.addAll(found);
        if (result.isEmpty()) {
            result.add(new Address(url.apply(requestHost == null ? "localhost" : requestHost), "адрес страницы", false));
        }
        return result;
    }

    /** QR-код ссылки в SVG: тёмные модули одним путём, без внешних сервисов */
    public String qrSvg(String text) {
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, Map.of(EncodeHintType.MARGIN, 2));
        } catch (WriterException e) {
            throw new IllegalArgumentException("Ссылку не превратить в QR-код", e);
        }
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + matrix.getWidth() + " " + matrix.getHeight()
                + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"QR-код приглашения\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path fill=\"#000\" d=\"" + path + "\"/></svg>";
    }
}
