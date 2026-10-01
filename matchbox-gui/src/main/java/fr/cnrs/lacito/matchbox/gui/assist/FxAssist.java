package fr.cnrs.lacito.matchbox.gui.assist;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javafx.application.Platform;

/** Bridges the assistant's futures to the JavaFX thread. */
final class FxAssist {

    private FxAssist() {}

    /**
     * Runs {@code onSuccess} or {@code onError} on the JavaFX thread when the future completes;
     * nothing is run if it was cancelled.
     */
    static <T> void onFx(CompletableFuture<T> future, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        future.whenComplete((value, error) -> {
            Throwable cause = error == null ? null : unwrap(error);
            if (cause instanceof CancellationException) {
                return;
            }
            Platform.runLater(() -> {
                if (cause == null) {
                    onSuccess.accept(value);
                } else {
                    onError.accept(cause);
                }
            });
        });
    }

    static Throwable unwrap(Throwable t) {
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    static String message(Throwable t) {
        Throwable cause = unwrap(t);
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
