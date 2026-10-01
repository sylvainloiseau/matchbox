package fr.cnrs.lacito.matchbox.assist.provider;

/**
 * Answer of a model.
 *
 * @param text         concatenated text of the answer
 * @param inputTokens  input tokens billed, -1 if not reported
 * @param outputTokens output tokens billed, -1 if not reported
 * @param cachedTokens input tokens read from the provider's prompt cache, -1 if not reported
 * @param truncated    true if the answer stopped at the token limit
 */
public record ChatResponse(String text, int inputTokens, int outputTokens, int cachedTokens, boolean truncated) {
}
