package fr.cnrs.lacito.matchbox.assist.provider;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A model of a provider. Calls are asynchronous; cancelling the returned future aborts the HTTP
 * request. Failures complete the future with an {@link AssistException}.
 */
public interface CompletionProvider {

    ProviderConfig config();

    CompletableFuture<ChatResponse> chat(ChatRequest request);

    /** Identifiers of the models available with this connection, sorted. */
    CompletableFuture<List<String>> listModels();
}
