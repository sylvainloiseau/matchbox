package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.LexiconMutationCommand;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectColumn;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import fr.cnrs.lacito.matchbox.gui.lexicon.EntrySnapshotPane;
import fr.cnrs.lacito.matchbox.gui.lexicon.StatusBadge;
import fr.cnrs.lacito.matchbox.gui.workbench.DictionaryHistoryService;
import fr.cnrs.lacito.matchbox.gui.workbench.Focus;
import fr.cnrs.lacito.matchbox.gui.workbench.Workbench;
import fr.cnrs.lacito.matchbox.model.lexicon.history.CommandKey;
import fr.cnrs.lacito.matchbox.model.lexicon.history.CommandStatus;
import fr.cnrs.lacito.matchbox.model.lexicon.history.CommandTargets;
import fr.cnrs.lacito.matchbox.model.lexicon.history.EntryRow;
import fr.cnrs.lacito.matchbox.model.lexicon.history.HistoryResult;
import fr.cnrs.lacito.matchbox.model.lexicon.history.OperationRow;
import java.util.List;
import java.util.Optional;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The view of a dictionary mutation command (built in, for every notation): in the tables, the
 * entry it is about, what became of it and its text; in the detail pane, where it is, its status,
 * the entry as it was after it (a read-only copy), what it did, and a link to the history of the
 * entry. Follows each new history of the dictionary.
 */
public class LexiconMutationCommandViewFactory implements ObjectViewFactory<LexiconMutationCommand> {

    @Override
    public Class<LexiconMutationCommand> getType() {
        return LexiconMutationCommand.class;
    }

    @Override
    public List<ObjectColumn<LexiconMutationCommand>> getColumns(ViewContext context) {
        Optional<DictionaryHistoryService> service = Workbench.of(context).map(Workbench::history);
        return List.of(
            new ObjectColumn<>("Entry", c -> service.map(s -> entryForm(s.result(), c)).orElseGet(
                () -> CommandTargets.entryForm(c.item()).orElse(""))),
            new ObjectColumn<>("Status", c -> service.map(s -> {
                CommandStatus st = s.computingProperty().get() ? CommandStatus.PENDING : s.result().statusOf(CommandKey.of(c));
                return StatusBadge.symbol(st) + " " + st.label();
            }).orElse("")),
            new ObjectColumn<>("Command", LexiconMutationCommand::sourceText));
    }

    /** The form of the entry a command is about: from the operations, else from the command itself. */
    static String entryForm(HistoryResult result, LexiconMutationCommand c) {
        return entryOf(result, c).map(e -> e.form(result.objectLanguages().isEmpty() ? "" : result.objectLanguages().get(0)))
            .orElseGet(() -> CommandTargets.entryForm(c.item()).orElse(""));
    }

    /** The entry a command changed (its operations), else the entry of its form. */
    static Optional<EntryRow> entryOf(HistoryResult result, LexiconMutationCommand c) {
        List<Integer> ops = result.commandOperations().getOrDefault(CommandKey.of(c), List.of());
        for (Integer op : ops) {
            Optional<EntryRow> e = result.entries().stream().filter(r -> r.operations().contains(op)).findFirst()
                .or(() -> result.deleted().stream().filter(r -> r.operations().contains(op)).findFirst());
            if (e.isPresent()) return e;
        }
        Optional<String> form = CommandTargets.entryForm(c.item());
        return form.flatMap(f -> result.entries().stream().filter(r -> r.forms().containsValue(f)).findFirst());
    }

    @Override
    public Node createView(LexiconMutationCommand command, ViewContext context) {
        Optional<Workbench> workbench = Workbench.of(context);
        if (workbench.isEmpty()) {
            return new Label(command.sourceText());
        }
        return new CommandView(command, context, workbench.get());
    }

    /** The detail of a command, rebuilt at each new history. */
    private static final class CommandView extends VBox {

        private final LexiconMutationCommand command;
        private final ViewContext context;
        private final Workbench workbench;
        private final DictionaryHistoryService service;
        private final Label header = new Label();
        private final HBox status = new HBox(8);
        private final VBox effects = new VBox(2);
        private final EntrySnapshotPane entry = new EntrySnapshotPane();
        private final Hyperlink history = new Hyperlink();
        @SuppressWarnings("FieldCanBeLocal")
        private final ChangeListener<Object> refresh = (obs, o, n) -> render();

        CommandView(LexiconMutationCommand command, ViewContext context, Workbench workbench) {
            this.command = command;
            this.context = context;
            this.workbench = workbench;
            this.service = workbench.history();
            setSpacing(6);
            setPadding(new Insets(6));
            setId("commandView");
            header.setStyle("-fx-font-weight: bold;");
            header.setWrapText(true);
            Label commandText = new Label(command.sourceText());
            commandText.setWrapText(true);
            commandText.setStyle("-fx-font-family: monospace;");
            history.setOnAction(e -> context.browse(command));
            getChildren().addAll(header, commandText, status, effects, history, entry);
            service.resultProperty().addListener(new WeakChangeListener<>(refresh));
            service.computingProperty().addListener(new WeakChangeListener<>(refresh));
            render();
        }

        private void render() {
            HistoryResult result = service.result();
            CommandKey key = CommandKey.of(command);
            CommandStatus st = service.computingProperty().get() || result.status() instanceof HistoryResult.Status.Computing
                ? CommandStatus.PENDING : result.statusOf(key);
            List<OperationRow> ops = result.operationsOf(key);
            String file = command.getTextCoordinate().text().path().getFileName().toString();
            String verb = ops.isEmpty() ? "" : " — " + ops.get(0).verb();
            String where = ops.isEmpty() ? "" : " (operation " + ops.get(ops.size() - 1).index() + " of "
                + result.operations().size() + ")";
            header.setText(file + ":" + command.getTextCoordinate().startLine() + verb + where);

            status.getChildren().setAll(new StatusBadge(st, explanation(result, st, ops)));
            Node action = action(result, st);
            if (action != null) status.getChildren().add(action);

            effects.getChildren().clear();
            for (OperationRow op : ops) {
                for (String effect : op.effects()) {
                    effects.getChildren().add(new Label("• " + effect));
                }
                if (op.error() != null) {
                    Label error = new Label("Error: " + op.error());
                    error.setWrapText(true);
                    error.setStyle("-fx-text-fill: #c62828;");
                    effects.getChildren().add(error);
                }
            }

            Optional<EntryRow> row = entryOf(result, command);
            String form = row.map(r -> r.form(result.objectLanguages().isEmpty() ? "" : result.objectLanguages().get(0)))
                .orElseGet(() -> CommandTargets.entryForm(command.item()).orElse(""));
            history.setText(form.isEmpty() ? "Show the dictionary history" : "Show history of " + form);
            history.setVisible(context.canBrowse(command));
            history.setManaged(history.isVisible());

            if (st == CommandStatus.PENDING) {
                entry.showMessage("Computing the history of the dictionary…");
                return;
            }
            if (row.isEmpty()) {
                entry.showMessage(form.isEmpty() ? "This command is about no entry." : "No entry " + form
                    + " in the dictionary at this point.");
                return;
            }
            int position = ops.isEmpty() ? result.shownPosition() : ops.get(ops.size() - 1).index();
            String id = row.get().key().id();
            service.entryAt(result, id, position).thenAccept(snapshot -> {
                if (service.result() != result) return;     // a newer history is shown
                if (snapshot.isPresent() && snapshot.get().entry().isEmpty() && !ops.isEmpty()) {
                    // deleted by this command: the state just before it
                    int before = ops.get(0).index() - 1;
                    service.entryAt(result, id, before).thenAccept(prev -> {
                        if (service.result() != result) return;
                        entry.show(prev.orElse(null), "Entry deleted by this command.");
                        entry.getChildren().add(0, new Label("Entry deleted by this command; as it was before:"));
                    });
                    return;
                }
                entry.show(snapshot.orElse(null), "No entry " + form + " at this point.");
                if (st == CommandStatus.NOT_TRIED) {
                    entry.getChildren().add(0, new Label("As it is in the context dictionary (no command applied):"));
                } else if (st == CommandStatus.NOT_APPLIED) {
                    entry.getChildren().add(0, new Label("As it is where applying stopped:"));
                } else if (st == CommandStatus.ROLLED_BACK) {
                    entry.getChildren().add(0, new Label("As it was after this command, before its file was rolled back:"));
                } else {
                    entry.getChildren().add(0, new Label("After this command:"));
                }
            }).exceptionally(e -> {
                java.util.logging.Logger.getLogger(LexiconMutationCommandViewFactory.class.getName())
                    .log(java.util.logging.Level.WARNING, "Cannot show the entry " + form, e);
                entry.showMessage("Cannot show the entry: " + (e.getCause() == null ? e : e.getCause()));
                return null;
            });
        }

        private String explanation(HistoryResult result, CommandStatus st, List<OperationRow> ops) {
            return switch (st) {
                case APPLIED -> "Applied to the dictionary";
                case NO_CHANGE -> "Applied; it changed nothing (what it asks for was already there)";
                case ERROR -> ops.stream().map(OperationRow::error).filter(e -> e != null).findFirst()
                    .orElse("This command has an error");
                case ROLLED_BACK -> result.status() instanceof HistoryResult.Status.StoppedAt s
                    ? "It ran, but " + name(s.text()) + ":" + s.line() + " failed later in the file: the file was rolled back"
                    : "Rolled back";
                case NOT_APPLIED -> result.status() instanceof HistoryResult.Status.StoppedAt s
                    ? "Not applied: applying stopped at " + name(s.text()) + ":" + s.line()
                    : "Not applied";
                case NOT_TRIED -> result.status() instanceof HistoryResult.Status.NotTried n
                    ? "Commands are applied only when the project has no LiftPatch syntax or static error ("
                        + n.errorCount() + " error" + (n.errorCount() == 1 ? "" : "s") + "; first in "
                        + name(n.firstText()) + ":" + n.firstLine() + ")"
                    : "Not tried";
                case PENDING -> "The history of the dictionary is being computed";
            };
        }

        /** A link to the error that explains the status, if it is elsewhere. */
        private Node action(HistoryResult result, CommandStatus st) {
            if ((st == CommandStatus.ROLLED_BACK || st == CommandStatus.NOT_APPLIED)
                && result.status() instanceof HistoryResult.Status.StoppedAt s) {
                return link("Show " + name(s.text()) + ":" + s.line(), s.text(), s.line());
            }
            if (st == CommandStatus.NOT_TRIED && result.status() instanceof HistoryResult.Status.NotTried n) {
                return link("Show first error", n.firstText(), n.firstLine());
            }
            return null;
        }

        private Hyperlink link(String label, TextId text, int line) {
            Hyperlink link = new Hyperlink(label);
            link.setOnAction(e -> {
                int offset = workbench.session().corpus().text(text).map(t -> lineStart(t, line)).orElse(0);
                workbench.navigator().showInEditor(Focus.at(text, offset));
            });
            return link;
        }
    }

    static String name(TextId text) {
        return text.path().getFileName() == null ? text.toString() : text.path().getFileName().toString();
    }

    /** The offset of the start of a 1-based line of a text. */
    static int lineStart(Text text, int line) {
        String content = text.content();
        int offset = 0;
        for (int l = 1; l < line && offset < content.length(); l++) {
            int nl = content.indexOf('\n', offset);
            if (nl < 0) return content.length();
            offset = nl + 1;
        }
        return offset;
    }
}
