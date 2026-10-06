package fr.cnrs.lacito.matchbox.assist;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import fr.cnrs.lacito.matchbox.assist.prompt.PromptBuilder;
import fr.cnrs.lacito.matchbox.assist.prompt.PromptPrefix;
import fr.cnrs.lacito.matchbox.assist.provider.AssistException;
import fr.cnrs.lacito.matchbox.assist.provider.ChatRequest;
import fr.cnrs.lacito.matchbox.assist.provider.ChatResponse;
import fr.cnrs.lacito.matchbox.assist.provider.CompletionProvider;
import fr.cnrs.lacito.matchbox.assist.provider.ProviderConfig;
import fr.cnrs.lacito.matchbox.assist.provider.ProviderFactory;
import fr.cnrs.lacito.matchbox.assist.provider.ProviderKind;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private String baseUrl;
    /** Responses to send, in order: status and body. */
    private final Deque<Map.Entry<Integer, String>> responses = new ArrayDeque<>();
    private final List<JsonNode> bodies = new CopyOnWriteArrayList<>();
    private final List<Map<String, List<String>>> headers = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().toString());
            headers.add(Map.copyOf(exchange.getRequestHeaders()));
            byte[] in = exchange.getRequestBody().readAllBytes();
            if (in.length > 0) bodies.add(MAPPER.readTree(in));
            Map.Entry<Integer, String> r;
            synchronized (responses) {
                r = responses.isEmpty() ? Map.entry(500, "{}") : responses.poll();
            }
            byte[] out = r.getValue().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(r.getKey(), out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void respond(int status, String body) {
        responses.add(Map.entry(status, body));
    }

    private CompletionProvider provider(ProviderKind kind, String model) {
        return new ProviderFactory(java.net.http.HttpClient.newHttpClient(), Duration.ofSeconds(5))
            .create(new ProviderConfig(kind, baseUrl + "/v1/", model, "secret-key"));
    }

    private static ChatRequest request(boolean schema) {
        AssistContext ctx = new AssistContext(new TextId(Path.of("f.txt")), "a [b\n", 4, List.of(new BracketGrammar()), List.of(),
            PromptPrefix.build(List.of(new BracketGrammar()), List.of(), false));
        return schema ? PromptBuilder.explain(ctx, new BracketGrammar().getResult(new TextId(Path.of("f.txt")), "a [b\n").diagnostics().get(0), "French")
            : PromptBuilder.complete(ctx, 4000, 1000, 15);
    }

    @Test
    void anthropicRequestCachesThePrefixAndSendsNoTemperature() {
        respond(200, """
            {"content":[{"type":"thinking","thinking":""},{"type":"text","text":"]"}],
             "stop_reason":"end_turn","usage":{"input_tokens":10,"output_tokens":2,"cache_read_input_tokens":8}}""");
        ChatResponse r = provider(ProviderKind.ANTHROPIC, "claude-sonnet-5-5").chat(request(true)).join();
        assertEquals("]", r.text());
        assertEquals(8, r.cachedTokens());
        assertEquals("/v1/messages", paths.get(0));
        JsonNode body = bodies.get(0);
        assertEquals("ephemeral", body.at("/system/0/cache_control/type").asText());
        assertTrue(body.path("temperature").isMissingNode());
        assertEquals("low", body.at("/output_config/effort").asText());
        assertEquals("json_schema", body.at("/output_config/format/type").asText());
        assertEquals(false, body.at("/output_config/format/schema/additionalProperties").asBoolean(true));
        // the fallback only applies on the Anthropic API itself
        assertTrue(body.path("fallbacks").isMissingNode());
        assertEquals("secret-key", headers.get(0).get("X-api-key").get(0));
        assertEquals("2023-06-01", headers.get(0).get("Anthropic-version").get(0));
    }

    @Test
    void anthropicHaikuGetsNoEffortAndRefusalsFail() {
        respond(200, """
            {"content":[],"stop_reason":"refusal","stop_details":{"type":"refusal","category":"cyber"}}""");
        CompletionException e = assertThrows(CompletionException.class,
            () -> provider(ProviderKind.ANTHROPIC, "claude-haiku-4-5").chat(request(false)).join());
        assertInstanceOf(AssistException.class, e.getCause());
        assertTrue(e.getCause().getMessage().contains("declined"));
        assertTrue(bodies.get(0).path("output_config").isMissingNode());
    }

    @Test
    void httpErrorsAreReadableAndTransientOnesRetried() {
        respond(529, "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}");
        respond(200, "{\"content\":[{\"type\":\"text\",\"text\":\"OK\"}],\"stop_reason\":\"end_turn\",\"usage\":{}}");
        assertEquals("OK", provider(ProviderKind.ANTHROPIC, "claude-haiku-4-5").chat(PromptBuilder.test()).join().text());

        respond(401, "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}");
        CompletionException e = assertThrows(CompletionException.class,
            () -> provider(ProviderKind.ANTHROPIC, "claude-haiku-4-5").chat(PromptBuilder.test()).join());
        AssistException ae = (AssistException) e.getCause();
        assertEquals(401, ae.getStatus());
        assertTrue(ae.getMessage().contains("API key") && ae.getMessage().contains("invalid x-api-key"), ae.getMessage());
    }

    @Test
    void openAiRequestUsesCompletionTokensAndJsonSchema() {
        respond(200, """
            {"choices":[{"message":{"role":"assistant","content":"{\\"explanation\\":\\"x\\"}"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":5,"completion_tokens":3,"prompt_tokens_details":{"cached_tokens":4}}}""");
        ChatResponse r = provider(ProviderKind.OPENAI, "some-model").chat(request(true)).join();
        assertEquals(4, r.cachedTokens());
        JsonNode body = bodies.get(0);
        assertEquals("/v1/chat/completions", paths.get(0));
        assertEquals("system", body.at("/messages/0/role").asText());
        assertTrue(body.has("max_completion_tokens"));
        assertTrue(body.path("temperature").isMissingNode());
        assertEquals("json_schema", body.at("/response_format/type").asText());
        assertTrue(body.at("/response_format/json_schema/strict").asBoolean());
        assertEquals("Bearer secret-key", headers.get(0).get("Authorization").get(0));
    }

    @Test
    void ollamaUsesMaxTokensAndListsModels() {
        respond(200, "{\"choices\":[{\"message\":{\"content\":\"]\"},\"finish_reason\":\"length\"}]}");
        respond(200, "{\"object\":\"list\",\"data\":[{\"id\":\"qwen3:8b\"},{\"id\":\"llama3.2\"}]}");
        CompletionProvider p = provider(ProviderKind.OLLAMA, "llama3.2");
        assertTrue(p.chat(request(false)).join().truncated());
        assertTrue(bodies.get(0).has("max_tokens"));
        assertEquals(0.2, bodies.get(0).path("temperature").asDouble());
        assertEquals(List.of("llama3.2", "qwen3:8b"), p.listModels().join());
        assertEquals("/v1/models", paths.get(1));
    }

    @Test
    void connectionFailuresAreExplained() {
        server.stop(0);
        CompletionException e = assertThrows(CompletionException.class,
            () -> provider(ProviderKind.OLLAMA, "m").chat(PromptBuilder.test()).join());
        assertTrue(e.getCause().getMessage().startsWith("Cannot connect"), e.getCause().getMessage());
    }

    @Test
    void keysAreMaskedInLogsAndBaseUrlsNormalized() {
        ProviderConfig c = new ProviderConfig(ProviderKind.OPENAI, "https://api.openai.com/v1/", "m", "sk-123");
        assertFalse(c.toString().contains("sk-123"));
        assertEquals("https://api.openai.com", c.baseUrl());
        assertEquals("http://localhost:11434", new ProviderConfig(ProviderKind.OLLAMA, "", "m", null).baseUrl());
    }
}
