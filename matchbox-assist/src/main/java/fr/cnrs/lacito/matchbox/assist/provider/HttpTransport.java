package fr.cnrs.lacito.matchbox.assist.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * JSON over HTTP with the JDK client: timeouts, a few retries of transient failures, and error
 * messages readable by the user.
 */
public final class HttpTransport {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_RETRIES = 2;
    private static final long MAX_RETRY_DELAY_MS = 5_000;

    private final HttpClient client;
    private final Duration requestTimeout;

    public HttpTransport(HttpClient client, Duration requestTimeout) {
        this.client = client;
        this.requestTimeout = requestTimeout;
    }

    public static HttpClient defaultClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public CompletableFuture<JsonNode> post(String url, Map<String, String> headers, JsonNode body) {
        String json;
        try {
            json = MAPPER.writeValueAsString(body);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(new AssistException("Cannot encode the request", e));
        }
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json));
        headers.forEach(b::header);
        return send(b.build(), 0);
    }

    public CompletableFuture<JsonNode> get(String url, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(requestTimeout).GET();
        headers.forEach(b::header);
        return send(b.build(), 0);
    }

    private CompletableFuture<JsonNode> send(HttpRequest request, int attempt) {
        CompletableFuture<HttpResponse<String>> exchange =
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        CompletableFuture<JsonNode> result = exchange
            .handle((response, error) -> {
                if (error != null) {
                    throw new CompletionException(networkError(request.uri(), unwrap(error)));
                }
                if (response.statusCode() / 100 == 2) {
                    return parse(response.body());
                }
                throw new CompletionException(httpError(response));
            })
            .exceptionallyCompose(error -> {
                Throwable cause = unwrap(error);
                if (cause instanceof AssistException ae && ae.isTransient() && attempt < MAX_RETRIES) {
                    long delay = Math.min(MAX_RETRY_DELAY_MS, 1_000L << attempt);
                    return CompletableFuture
                        .supplyAsync(() -> null, CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS))
                        .thenCompose(x -> send(request, attempt + 1));
                }
                return CompletableFuture.failedFuture(cause);
            });
        // cancelling the result aborts the HTTP exchange
        result.whenComplete((r, e) -> {
            if (result.isCancelled()) {
                exchange.cancel(true);
            }
        });
        return result;
    }

    private static JsonNode parse(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (IOException e) {
            throw new CompletionException(new AssistException("The server returned an unreadable answer", e));
        }
    }

    static Throwable unwrap(Throwable t) {
        while ((t instanceof CompletionException || t instanceof java.util.concurrent.ExecutionException)
                && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private static AssistException networkError(URI uri, Throwable cause) {
        if (cause instanceof AssistException ae) {
            return ae;
        }
        if (cause instanceof HttpConnectTimeoutException || cause instanceof ConnectException) {
            return new AssistException("Cannot connect to " + uri.getScheme() + "://" + uri.getAuthority() +
                " (is the server running and the address correct?)", cause);
        }
        if (cause instanceof HttpTimeoutException) {
            return new AssistException("The server did not answer in time", 408, cause);
        }
        return new AssistException("Network error: " + cause.getMessage(), cause);
    }

    private static AssistException httpError(HttpResponse<String> response) {
        int status = response.statusCode();
        String detail = errorDetail(response.body());
        String message = switch (status) {
            case 400 -> "The request was rejected";
            case 401 -> "Invalid or missing API key";
            case 402 -> "Billing problem with the provider account";
            case 403 -> "The API key does not give access to this resource";
            case 404 -> "Unknown model or address";
            case 408 -> "The server did not answer in time";
            case 413 -> "The request is too large";
            case 429 -> "Too many requests or quota exceeded";
            case 529 -> "The provider is overloaded";
            default -> status >= 500 ? "Server error" : "Request failed";
        };
        return new AssistException(message + " (HTTP " + status + (detail.isEmpty() ? "" : ": " + detail) + ")",
            status, null);
    }

    /** The error message of the providers' JSON error bodies, or the start of the body. */
    private static String errorDetail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            JsonNode error = node.path("error");
            if (error.isTextual()) return error.asText();
            if (error.path("message").isTextual()) return error.path("message").asText();
            if (node.path("message").isTextual()) return node.path("message").asText();
        } catch (IOException e) {
            // not JSON
        }
        String s = body.strip();
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
