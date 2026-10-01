package fr.cnrs.lacito.matchbox.assist.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Claude, through the Anthropic Messages API ({@code POST /v1/messages}).
 *
 * <ul>
 *   <li>System blocks are sent as text blocks; the block marked as cache breakpoint gets
 *       {@code cache_control: {type: ephemeral}} so that the stable prefix is cached.</li>
 *   <li>No sampling parameters: current models reject {@code temperature}.</li>
 *   <li>Effort goes in {@code output_config.effort}, except on Haiku 4.5, which rejects it.
 *       The JSON schema goes in {@code output_config.format}.</li>
 *   <li>Server-side refusal fallback ({@code fallbacks: "default"}) is requested on the models
 *       that support it, on the Anthropic API itself.</li>
 * </ul>
 */
public final class AnthropicProvider implements CompletionProvider {

    static final String API_VERSION = "2023-06-01";
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    private static final Set<String> FALLBACK_MODELS =
        Set.of("claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5");

    private final ProviderConfig config;
    private final HttpTransport http;

    public AnthropicProvider(ProviderConfig config, HttpTransport http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public ProviderConfig config() {
        return config;
    }

    private Map<String, String> headers(boolean fallback) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("x-api-key", config.apiKey());
        h.put("anthropic-version", API_VERSION);
        if (fallback) {
            h.put("anthropic-beta", FALLBACK_BETA);
        }
        return h;
    }

    private boolean usesFallback() {
        return FALLBACK_MODELS.contains(config.model()) && config.baseUrl().equals(ProviderKind.ANTHROPIC.defaultBaseUrl());
    }

    ObjectNode body(ChatRequest request) {
        ObjectNode body = HttpTransport.MAPPER.createObjectNode();
        body.put("model", config.model());
        body.put("max_tokens", request.maxTokens());
        ArrayNode system = body.putArray("system");
        for (ChatRequest.SystemBlock block : request.system()) {
            ObjectNode b = system.addObject();
            b.put("type", "text");
            b.put("text", block.text());
            if (block.cacheBreakpoint()) {
                b.putObject("cache_control").put("type", "ephemeral");
            }
        }
        ArrayNode messages = body.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", request.user());

        ObjectNode outputConfig = HttpTransport.MAPPER.createObjectNode();
        if (!config.model().startsWith("claude-haiku")) {
            outputConfig.put("effort", request.effort().name().toLowerCase(java.util.Locale.ROOT));
        }
        request.schema().ifPresent(schema -> {
            ObjectNode format = outputConfig.putObject("format");
            format.put("type", "json_schema");
            format.set("schema", schema.schema());
        });
        if (!outputConfig.isEmpty()) {
            body.set("output_config", outputConfig);
        }
        if (usesFallback()) {
            body.put("fallbacks", "default");
        }
        return body;
    }

    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request) {
        return http.post(config.baseUrl() + "/v1/messages", headers(usesFallback()), body(request))
            .thenApply(AnthropicProvider::response);
    }

    static ChatResponse response(JsonNode json) {
        String stopReason = json.path("stop_reason").asText("");
        if (stopReason.equals("refusal")) {
            String category = json.path("stop_details").path("category").asText("");
            throw new AssistException("The model declined this request" +
                (category.isEmpty() ? "" : " (" + category + ")"));
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode block : json.path("content")) {
            // thinking blocks and others are ignored
            if (block.path("type").asText().equals("text")) {
                text.append(block.path("text").asText());
            }
        }
        JsonNode usage = json.path("usage");
        return new ChatResponse(
            text.toString(),
            usage.path("input_tokens").asInt(-1),
            usage.path("output_tokens").asInt(-1),
            usage.path("cache_read_input_tokens").asInt(-1),
            stopReason.equals("max_tokens")
        );
    }

    @Override
    public CompletableFuture<List<String>> listModels() {
        return http.get(config.baseUrl() + "/v1/models?limit=1000", headers(false))
            .thenApply(json -> {
                List<String> ids = new ArrayList<>();
                for (JsonNode m : json.path("data")) {
                    ids.add(m.path("id").asText());
                }
                ids.sort(null);
                return ids;
            });
    }
}
