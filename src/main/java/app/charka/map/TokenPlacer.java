package app.charka.map;

import app.charka.model.EncounterParticipant;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Расстановка фишек без позиции: в зоны старта своей стороны, без зон — игроки слева, NPC справа.
 * Не встаёт на занятую клетку и в предмет, через который не пройти; места нет — фишка остаётся в лотке «Не на карте».
 */
public final class TokenPlacer {

    private TokenPlacer() {
    }

    public static void place(List<EncounterParticipant> participants, MapGrid grid) {
        MapDoc doc = grid.doc();
        Set<Long> taken = new HashSet<>();
        participants.stream().filter(p -> p.getPosX() != null && p.getPosY() != null)
                .forEach(p -> taken.add(key(p.getPosX(), p.getPosY())));
        for (EncounterParticipant participant : participants) {
            if (participant.getPosX() != null && participant.getPosY() != null) {
                continue;
            }
            boolean npc = participant.getCharacter().isNpc();
            String kind = npc ? MapDoc.NPCS : MapDoc.PLAYERS;
            List<int[]> spots = new ArrayList<>();
            doc.zones().stream().filter(zone -> kind.equals(zone.kind())).forEach(zone -> {
                for (int y = zone.y(); y < zone.y() + zone.h(); y++) {
                    for (int x = zone.x(); x < zone.x() + zone.w(); x++) {
                        spots.add(new int[]{x, y});
                    }
                }
            });
            if (spots.isEmpty()) {
                // колонки от края внутрь, строки от середины
                for (int column = 0; column < doc.w(); column++) {
                    int x = npc ? doc.w() - 1 - column : column;
                    for (int i = 0; i < doc.h(); i++) {
                        int y = doc.h() / 2 + (i % 2 == 0 ? i / 2 : -(i / 2 + 1));
                        spots.add(new int[]{x, y});
                    }
                }
            }
            for (int[] spot : spots) {
                if (!grid.blocked(spot[0], spot[1]) && taken.add(key(spot[0], spot[1]))) {
                    participant.setPosX(spot[0]);
                    participant.setPosY(spot[1]);
                    break;
                }
            }
        }
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }
}
