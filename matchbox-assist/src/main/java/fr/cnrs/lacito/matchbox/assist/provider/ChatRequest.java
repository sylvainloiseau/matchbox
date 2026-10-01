package fr.cnrs.lacito.matchbox.assist.provider;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A single-turn request: system blocks, then one user message.
 *
 * @param task      short name of the task, for logs ("complete", "explain", "review", "test")
 * @param system    system blocks in order; the last block marked {@code cacheBreakpoint} ends the
 *                  part that providers may cache
 * @param user      the user message
 * @param maxTokens maximum number of output tokens
 * @param effort    how much reasoning the task deserves; providers without such a setting ignore it
 * @param schema    JSON schema the answer must follow, if any
 */
public record ChatRequest(String task, List<SystemBlock> system, String user, int maxTokens,
                          Effort effort, Optional<OutputSchema> schema) {

    public ChatRequest {
        Objects.requireNonNull(task, "task");
        system = List.copyOf(system);
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(effort, "effort");
        Objects.requireNonNull(schema, "schema");
    }

    /** A block of the system prompt. */
    public record SystemBlock(String text, boolean cacheBreakpoint) {
        public SystemBlock {
            Objects.requireNonNull(text, "text");
        }
    }

    /** A JSON schema for structured output (an object with {@code additionalProperties: false}). */
    public record OutputSchema(String name, ObjectNode schema) {
        public OutputSchema {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(schema, "schema");
        }
    }

    public enum Effort { LOW, MEDIUM, HIGH }

    /** Number of characters sent, for logs. */
    public int length() {
        return system.stream().mapToInt(b -> b.text().length()).sum() + user.length();
    }
}
