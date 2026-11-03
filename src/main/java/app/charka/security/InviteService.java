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
     * Адреса ноутбука в локальной сети, по которым телефон откроет приложение: IPv4 включённых сетевых карт, кроме петли.
     * Если страницу открыли не с localhost — её адрес первым.
     */
    public List<String> addresses(String scheme, String requestHost, int port) {
        List<String> hosts = new ArrayList<>();
        if (requestHost != null && !requestHost.equals("localhost") && !requestHost.startsWith("127.") && !requestHost.equals("[::1]")) {
            hosts.add(requestHost);
        }
        // локальная сеть (192.168, 10, 172.16–31) — первой: её видит телефон за тем же wi-fi
        List<String> local = new ArrayList<>();
        List<String> other = new ArrayList<>();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
                        (address.isSiteLocalAddress() ? local : other).add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            // сетевых карт не прочитать — остаётся адрес страницы
        }
        java.util.stream.Stream.concat(local.stream(), other.stream()).filter(host -> !hosts.contains(host)).forEach(hosts::add);
        if (hosts.isEmpty()) {
            hosts.add(requestHost == null ? "localhost" : requestHost);
        }
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return hosts.stream().map(host -> scheme + "://" + host + (defaultPort ? "" : ":" + port)).toList();
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
