package app.charka.exception;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

public class InventoryNotFoundException extends NoSuchElementException {
    public InventoryNotFoundException(String message) {
        super(message);
    }
    public static Supplier<InventoryNotFoundException> forId(Long id) {
        return () ->
                new InventoryNotFoundException(String.format("Inventory not found for ID = %d", id));
    }
}
