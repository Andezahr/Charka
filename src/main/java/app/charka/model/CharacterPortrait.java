package app.charka.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * PH-20: портрет персонажа — квадратная картинка до 512 px. Не игровое состояние: отмена его не снимает,
 * персонаж о нём не знает (байты не грузятся вместе с листом).
 */
@Getter
@Setter
@Entity
@Table(name = "character_portrait")
public class CharacterPortrait {

    @Id
    @Column(name = "character_id")
    private Long characterId;

    private byte[] data;

    /** MIME-тип: image/webp, image/jpeg, image/png */
    @Column(name = "content_type")
    private String contentType;

    /** Когда загружен; версия в адресе картинки, поэтому её можно кэшировать надолго */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Тот же портрет другому персонажу (копии NPC, импорт) */
    public CharacterPortrait copyFor(Long otherCharacterId) {
        CharacterPortrait copy = new CharacterPortrait();
        copy.setCharacterId(otherCharacterId);
        copy.setData(data);
        copy.setContentType(contentType);
        copy.setUpdatedAt(updatedAt);
        return copy;
    }
}
