package fr.cnrs.lacito.matchbox.gui.properties;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** The inherited context: a LIFT dictionary and a Matchbox corpus the project's data is added to. */
final class InheritedContextGroup {

    private final PageContext ctx;
    private final VBox node;

    /** @param dictionaryChanged called when the context dictionary is chosen or cleared */
    InheritedContextGroup(PageContext ctx, Runnable dictionaryChanged) {
        this.ctx = ctx;
        VBox dictionary = fileRow("contextDictionary", "Context dictionary",
            "Provide a dictionary if you want the lexical information provided in your project to be added to it."
                + " The dictionary should be in LIFT format.",
            ctx.edit().getContextDictionary(), p -> {
                ctx.edit().setContextDictionary(p);
                dictionaryChanged.run();
            },
            new FileChooser.ExtensionFilter("LIFT dictionaries", "*.lift"));
        VBox corpus = fileRow("contextCorpus", "Context corpus",
            "Provide a corpus if you want the data provided in your project to be added to it."
                + " The corpus should be a Matchbox corpus.",
            ctx.edit().getContextCorpus(), ctx.edit()::setContextCorpus,
            new FileChooser.ExtensionFilter("Matchbox projects", "*.mbx"));
        node = DialogParts.group("Inherited context", dictionary, corpus);
    }

    VBox node() {
        return node;
    }

    /**
     * A file of the inherited context: label, read-only path, "Choose…" (file chooser) and "×"
     * (none), with an explanation below.
     *
     * @param id prefix of the controls' ids ({@code <id>Field}, {@code <id>Choose}, {@code <id>Clear})
     */
    private VBox fileRow(String id, String title, String explanation, Optional<Path> value,
                         Consumer<Path> setter, FileChooser.ExtensionFilter filter) {
        Label label = new Label(title);
        label.setMinWidth(130);
        TextField field = new TextField(value.map(Path::toString).orElse(""));
        field.setId(id + "Field");
        field.setEditable(false);
        field.setPromptText("None");
        HBox.setHgrow(field, Priority.ALWAYS);
        Button choose = new Button("Choose…");
        choose.setId(id + "Choose");
        choose.setTooltip(new Tooltip("Select the file of the " + title.toLowerCase(java.util.Locale.ROOT)));
        Button clear = new Button("×");
        clear.setId(id + "Clear");
        clear.setTooltip(new Tooltip("No " + title.toLowerCase(java.util.Locale.ROOT)));
        clear.disableProperty().bind(field.textProperty().isEmpty());
        choose.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(title);
            chooser.getExtensionFilters().addAll(filter, new FileChooser.ExtensionFilter("All files", "*.*"));
            initialDirectory(field.getText()).ifPresent(chooser::setInitialDirectory);
            File selected = chooser.showOpenDialog(ctx.stage());
            if (selected != null) {
                setter.accept(selected.toPath());
                field.setText(selected.toPath().toString());
                ctx.changed();
            }
        });
        clear.setOnAction(e -> {
            setter.accept(null);
            field.setText("");
            ctx.changed();
        });
        HBox row = new HBox(5, label, field, choose, clear);
        row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(3, row, DialogParts.help(explanation));
    }

    /** Where a file chooser opens: the directory of the current file, else of the project file. */
    private Optional<File> initialDirectory(String current) {
        File base = !current.isEmpty() ? new File(current).getParentFile()
            : ctx.project().directory().map(Path::toFile).orElse(null);
        return Optional.ofNullable(base).filter(File::isDirectory);
    }
}
