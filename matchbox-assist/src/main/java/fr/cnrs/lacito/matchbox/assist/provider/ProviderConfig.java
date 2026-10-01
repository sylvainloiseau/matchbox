package fr.cnrs.lacito.matchbox.assist.provider;

import java.util.Objects;

/**
 * Connection to one model of a provider.
 *
 * @param kind    the provider
 * @param baseUrl server URL without the API path (e.g. {@code https://api.openai.com}); a trailing
 *                {@code /v1} or slash is ignored
 * @param model   model identifier
 * @param apiKey  API key, empty if none; never logged ({@link #toString()} masks it)
 */
public record ProviderConfig(ProviderKind kind, String baseUrl, String model, String apiKey) {

    public ProviderConfig {
        Objects.requireNonNull(kind, "kind");
        baseUrl = normalizeBaseUrl(baseUrl == null || baseUrl.isBlank() ? kind.defaultBaseUrl() : baseUrl);
        model = model == null ? "" : model.strip();
        apiKey = apiKey == null ? "" : apiKey.strip();
    }

    static String normalizeBaseUrl(String url) {
        String u = url.strip();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (u.endsWith("/v1")) {
            u = u.substring(0, u.length() - 3);
        }
        return u;
    }

    public ProviderConfig withModel(String otherModel) {
        return new ProviderConfig(kind, baseUrl, otherModel, apiKey);
    }

    @Override
    public String toString() {
        return "ProviderConfig[kind=" + kind.id() + ", baseUrl=" + baseUrl + ", model=" + model +
            ", apiKey=" + (apiKey.isEmpty() ? "none" : "***") + "]";
    }
}
