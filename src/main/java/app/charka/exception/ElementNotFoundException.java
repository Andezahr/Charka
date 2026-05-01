package app.charka.exception;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

public class ElementNotFoundException extends NoSuchElementException {
    public ElementNotFoundException(String exceptionText) {
        super(exceptionText);
    }

    public static Supplier<ElementNotFoundException> forId(Long id) {
        return () -> new ElementNotFoundException(
                String.format("Element not found for ID = %d", id)
        );
    }
}
