package fr.cnrs.lacito.matchbox.assist;

/**
 * Options of the requests.
 *
 * @param contextBefore       characters sent before the cursor for a completion
 * @param contextAfter        characters sent after the cursor for a completion
 * @param maxCompletionLines  maximum number of lines of a completion
 * @param language            language of explanations (e.g. "English", "French")
 */
public record AssistOptions(int contextBefore, int contextAfter, int maxCompletionLines, String language) {

    public static AssistOptions defaults() {
        return new AssistOptions(4000, 1000, 15, "English");
    }
}
