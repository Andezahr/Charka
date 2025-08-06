package app.charka.exception;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

public class CharacterNotFoundException extends NoSuchElementException {
    public CharacterNotFoundException(String exceptionText) {
        super(exceptionText);
    }

    public static Supplier<CharacterNotFoundException> forId(Long id) {
        return () -> new CharacterNotFoundException(
                String.format("Character not found for ID = %d", id)
        );
    }
}
