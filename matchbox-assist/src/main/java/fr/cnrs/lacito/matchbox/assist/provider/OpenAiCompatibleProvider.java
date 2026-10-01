package fr.cnrs.lacito.matchbox.assist.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The OpenAI Chat Completions API ({@code POST /v1/chat/completions}), used for ChatGPT and for
 * the OpenAI-compatible endpoint of Ollama (and of LM Studio, vLLM…).
 *
 * <p>System blocks are joined into one system message, in order: OpenAI caches long identical
 * prefixes automatically, and Ollama reuses its KV cache for an identical prefix.
 */
public final class OpenAiCompatibleProvider implements CompletionProvider {

    private final ProviderConfig config;
    private final HttpTransport http;

    public OpenAiCompatibleProvider(ProviderConfig config, HttpTransport http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public ProviderConfig config() {
        return config;
    }

    private Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<>();
        if (!config.apiKey().isEmpty()) {
            h.put("Authorization", "Bearer " + config.apiKey());
        }
        return h;
    }

    private boolean isOpenAi() {
        return config.kind() == ProviderKind.OPENAI;
    }

    ObjectNode body(ChatRequest request) {
        ObjectNode body = HttpTransport.MAPPER.createObjectNode();
        body.put("model", config.model());
        ArrayNode messages = body.putArray("messages");
        StringBuilder system = new StringBuilder();
        for (ChatRequest.SystemBlock block : request.system()) {
            if (!system.isEmpty()) system.append("\n\n");
            system.append(block.text());
        }
        if (!system.isEmpty()) {
            messages.addObject().put("role", "system").put("content", system.toString());
        }
        messages.addObject().put("role", "user").put("content", request.user());
        if (isOpenAi()) {
            // reasoning models reject max_tokens and non-default temperatures
            body.put("max_completion_tokens", request.maxTokens());
        } else {
            body.put("max_tokens", request.maxTokens());
            body.put("temperature", 0.2);
        }
        request.schema().ifPresent(schema -> {
            ObjectNode format = body.putObject("response_format");
            format.put("type", "json_schema");
            ObjectNode jsonSchema = format.putObject("json_schema");
            jsonSchema.put("name", schema.name());
            jsonSchema.put("strict", true);
            jsonSchema.set("schema", schema.schema());
        });
        return body;
    }

    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request) {
        return http.post(config.baseUrl() + "/v1/chat/completions", headers(), body(request))
            .thenApply(OpenAiCompatibleProvider::response);
    }

    static ChatResponse response(JsonNode json) {
        JsonNode choice = json.path("choices").path(0);
        JsonNode message = choice.path("message");
        if (message.path("refusal").isTextual() && !message.path("refusal").asText().isBlank()) {
            throw new AssistException("The model declined this request: " + message.path("refusal").asText());
        }
        JsonNode usage = json.path("usage");
        return new ChatResponse(
            message.path("content").asText(""),
            usage.path("prompt_tokens").asInt(-1),
            usage.path("completion_tokens").asInt(-1),
            usage.path("prompt_tokens_details").path("cached_tokens").asInt(-1),
            choice.path("finish_reason").asText("").equals("length")
        );
    }

    @Override
    public CompletableFuture<List<String>> listModels() {
        return http.get(config.baseUrl() + "/v1/models", headers())
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
