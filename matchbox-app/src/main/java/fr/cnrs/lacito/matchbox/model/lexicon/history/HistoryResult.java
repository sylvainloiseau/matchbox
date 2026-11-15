package fr.cnrs.lacito.matchbox.model.lexicon.history;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The history of the dictionary, as computed for one version of the project: immutable, read on
 * any thread. It holds no object of the dictionary model; entries are read through
 * {@link DictionaryHistory#entryAt} as detached copies.
 *
 * @param generation       the recompute it comes from (a query names it)
 * @param status           whether the commands were applied, and where they stopped
 * @param startedEmpty     whether there is no context dictionary (the history starts from an empty one)
 * @param commandCount     the number of top-level commands of the project
 * @param fileCount        the number of files holding commands
 * @param statuses         what became of each command (top-level and inner)
 * @param commandOperations the operations of each command, in order
 * @param operations       every operation, by its index (1-based; index 0 unused)
 * @param entries          the entries of the dictionary shown (the end of what was applied; the
 *                         context dictionary when not tried)
 * @param deleted          the entries the commands deleted, as they were before
 * @param objectLanguages  the object languages of that dictionary
 * @param metaLanguages    the meta languages of that dictionary
 * @param diagnostics      the problems found by applying the commands (dynamic errors and warnings,
 *                         a dictionary that cannot be read), by text
 * @param shownPosition    the position of the timeline the entries are read at
 */
public record HistoryResult(long generation, Status status, boolean startedEmpty, int commandCount, int fileCount,
                            Map<CommandKey, CommandStatus> statuses, Map<CommandKey, List<Integer>> commandOperations,
                            List<OperationRow> operations, List<EntryRow> entries, List<EntryRow> deleted,
                            List<String> objectLanguages, List<String> metaLanguages,
                            Map<TextId, List<Diagnostic>> diagnostics, int shownPosition) {

    public HistoryResult {
        statuses = Map.copyOf(statuses);
        commandOperations = Map.copyOf(commandOperations);
        operations = List.copyOf(operations);
        entries = List.copyOf(entries);
        deleted = List.copyOf(deleted);
        objectLanguages = List.copyOf(objectLanguages);
        metaLanguages = List.copyOf(metaLanguages);
        diagnostics = Map.copyOf(diagnostics);
    }

    /** Where the history stands. */
    public sealed interface Status {

        /** Every command was applied: the patched dictionary is available. */
        record Clean() implements Status {}

        /** Syntax or static errors in the project: no command was tried (gate 1). */
        record NotTried(int errorCount, TextId firstText, int firstLine, String firstMessage) implements Status {}

        /** Applying stopped at the first dynamic error (gate 2): its file was rolled back. */
        record StoppedAt(TextId text, int line, String message, int laterCommands) implements Status {}

        /** The context dictionary cannot be read. */
        record Failed(String message) implements Status {}

        /** Being computed for the first time. */
        record Computing() implements Status {}
    }

    /** The result before anything was computed. */
    public static HistoryResult computing() {
        return new HistoryResult(-1, new Status.Computing(), false, 0, 0, Map.of(), Map.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), Map.of(), 0);
    }

    /** The result of a project without command nor context dictionary. */
    public boolean isEmpty() {
        return commandCount == 0 && entries.isEmpty() && !(status instanceof Status.Failed);
    }

    /** Whether the patched dictionary is available (export, dictionary check). */
    public boolean isClean() {
        return status instanceof Status.Clean;
    }

    public CommandStatus statusOf(CommandKey key) {
        return statuses.getOrDefault(key, status instanceof Status.Computing ? CommandStatus.PENDING
            : CommandStatus.NOT_APPLIED);
    }

    public List<OperationRow> operationsOf(CommandKey key) {
        return commandOperations.getOrDefault(key, List.of()).stream().map(i -> operations.get(i - 1)).toList();
    }

    /** The operation of an index. */
    public Optional<OperationRow> operation(int index) {
        return index >= 1 && index <= operations.size() ? Optional.of(operations.get(index - 1)) : Optional.empty();
    }

    public List<Diagnostic> diagnosticsOf(TextId text) {
        return diagnostics.getOrDefault(text, List.of());
    }

    /** The entry of a key in this history, if it is there. */
    public Optional<EntryRow> entry(EntryKey key) {
        return entries.stream().filter(e -> e.key().matches(key)).findFirst()
            .or(() -> deleted.stream().filter(e -> e.key().matches(key)).findFirst());
    }
}
