package fr.cnrs.lacito.matchbox.assist.provider;

import java.util.Arrays;
import java.util.Optional;

/** The model providers the assistant can use. */
public enum ProviderKind {

    NONE("none", "Disabled", "", "", "", ""),
    ANTHROPIC("anthropic", "Claude (Anthropic)", "https://api.anthropic.com", "ANTHROPIC_API_KEY",
        "claude-haiku-4-5", "claude-sonnet-5-5"),
    OPENAI("openai", "ChatGPT (OpenAI)", "https://api.openai.com", "OPENAI_API_KEY", "", ""),
    OLLAMA("ollama", "Ollama (local)", "http://localhost:11434", "", "", "");

    private final String id;
    private final String displayName;
    private final String defaultBaseUrl;
    private final String apiKeyVariable;
    private final String defaultFastModel;
    private final String defaultSmartModel;

    ProviderKind(String id, String displayName, String defaultBaseUrl, String apiKeyVariable,
                 String defaultFastModel, String defaultSmartModel) {
        this.id = id;
        this.displayName = displayName;
        this.defaultBaseUrl = defaultBaseUrl;
        this.apiKeyVariable = apiKeyVariable;
        this.defaultFastModel = defaultFastModel;
        this.defaultSmartModel = defaultSmartModel;
    }

    /** Identifier stored in the settings. */
    public String id() { return id; }
    public String displayName() { return displayName; }
    public String defaultBaseUrl() { return defaultBaseUrl; }
    /** Environment variable holding the API key; empty if the provider needs none. */
    public String apiKeyVariable() { return apiKeyVariable; }
    /** Model suggested for inline completion (latency); empty if none is suggested. */
    public String defaultFastModel() { return defaultFastModel; }
    /** Model suggested for explanations and reviews; empty if none is suggested. */
    public String defaultSmartModel() { return defaultSmartModel; }

    public boolean needsApiKey() {
        return !apiKeyVariable.isEmpty();
    }

    public static Optional<ProviderKind> fromId(String id) {
        return Arrays.stream(values()).filter(k -> k.id.equals(id)).findFirst();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
