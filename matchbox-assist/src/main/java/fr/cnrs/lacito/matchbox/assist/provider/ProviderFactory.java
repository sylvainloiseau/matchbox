package fr.cnrs.lacito.matchbox.assist.provider;

import java.net.http.HttpClient;
import java.time.Duration;

/** Creates the provider of a configuration. */
public final class ProviderFactory {

    private final HttpClient client;
    private final Duration requestTimeout;

    public ProviderFactory() {
        this(HttpTransport.defaultClient(), Duration.ofSeconds(120));
    }

    public ProviderFactory(HttpClient client, Duration requestTimeout) {
        this.client = client;
        this.requestTimeout = requestTimeout;
    }

    /** @throws IllegalArgumentException if the provider is {@link ProviderKind#NONE} */
    public CompletionProvider create(ProviderConfig config) {
        HttpTransport http = new HttpTransport(client, requestTimeout);
        return switch (config.kind()) {
            case ANTHROPIC -> new AnthropicProvider(config, http);
            case OPENAI, OLLAMA -> new OpenAiCompatibleProvider(config, http);
            case NONE -> throw new IllegalArgumentException("No provider selected");
        };
    }
}
