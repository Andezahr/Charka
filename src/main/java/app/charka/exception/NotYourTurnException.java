package app.charka.exception;

import app.charka.encounter.RoundActions;

/**
 * TURN-01: игрок в бою вне своего хода. Страница — возврат назад с подписью ({@link GlobalExceptionHandler}),
 * REST и карта — 400 текстом, как любой {@link IllegalArgumentException}.
 */
public class NotYourTurnException extends IllegalArgumentException {

    public NotYourTurnException() {
        super(RoundActions.NOT_YOUR_TURN);
    }
}
