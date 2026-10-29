package app.charka.exception;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * Сущность не найдена → страница 404 ({@link GlobalExceptionHandler}).
 * Общий для сущностей без своего исключения; {@link CharacterNotFoundException} и {@link InventoryNotFoundException} — наследники.
 */
public class NotFoundException extends NoSuchElementException {

    public NotFoundException(String message) {
        super(message);
    }

    /**
     * @param entity название сущности для сообщения («Item», «Wound»)
     */
    public static Supplier<NotFoundException> forId(String entity, Long id) {
        return () -> new NotFoundException(String.format("%s not found for id=%d", entity, id));
    }
}
