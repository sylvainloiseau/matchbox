package fr.cnrs.lacito.matchbox.gui.properties;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.model.project.FileType;
import fr.cnrs.lacito.matchbox.model.project.ProjectEdit;
import fr.cnrs.lacito.matchbox.model.project.ProjectEdit.FileTypeDraft;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** The file types (a regular expression on file names) and the grammars applied to them, in rank order. */
final class FileTypesPage implements PropertiesPage {

    private final PageContext ctx;
    private final ObservableList<FileTypeDraft> fileTypes = FXCollections.observableArrayList();
    private final ObservableList<GrammarPlugin> grammarRows = FXCollections.observableArrayList();
    private final ListView<FileTypeDraft> fileTypeList;
    private final ListView<GrammarPlugin> grammarList;
    private final TextField nameField = new TextField();
    private final TextField regexField = new TextField();
    private final Label nameError = DialogParts.errorLabel();
    private final Label regexError = DialogParts.errorLabel();
    private final VBox content;
    private FileTypeDraft current;
    /** True while the detail fields are filled from a draft (their listeners must not write back). */
    private boolean loading;

    FileTypesPage(PageContext ctx) {
        this.ctx = ctx;
        ProjectEdit edit = ctx.edit();
        fileTypes.setAll(edit.getFileTypes());
        fileTypeList = new ListView<>(fileTypes);
        fileTypeList.setId("fileTypeList");
        fileTypeList.setPrefHeight(200);
        fileTypeList.setPrefWidth(220);
        fileTypeList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> showFileType(n));
        Button add = new Button("Add");
        add.setId("addFileType");
        add.setTooltip(new Tooltip("A new file type, matching every file until you change its expression"));
        add.setOnAction(e -> {
            FileTypeDraft draft = new FileTypeDraft("New file type", ".+", List.of());
            edit.getFileTypes().add(draft);
            fileTypes.add(draft);
            fileTypeList.getSelectionModel().select(draft);
            nameField.requestFocus();
            nameField.selectAll();
            ctx.changed();
        });
        Button remove = new Button("Remove");
        remove.setId("removeFileType");
        remove.disableProperty().bind(fileTypeList.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(e -> {
            FileTypeDraft draft = fileTypeList.getSelectionModel().getSelectedItem();
            edit.getFileTypes().remove(draft);
            fileTypes.remove(draft);
            ctx.changed();
        });
        VBox left = new VBox(5, DialogParts.bold("File types:"), fileTypeList, new HBox(5, add, remove));
        VBox.setVgrow(fileTypeList, Priority.ALWAYS);

        nameField.setId("fileTypeName");
        nameField.setPromptText("e.g. 'Conll files', 'Old fieldwork'");
        nameField.textProperty().addListener((obs, o, n) -> {
            if (current != null && !loading) {
                current.setName(n);
                fileTypeList.refresh();
                ctx.changed();
            }
            showProblems();
        });
        regexField.setId("fileTypeRegex");
        regexField.setPromptText("e.g. .*\\.md");
        regexField.textProperty().addListener((obs, o, n) -> {
            if (current != null && !loading) {
                current.setRegex(n);
                ctx.changed();
            }
            showProblems();
        });

        grammarList = new ListView<>(grammarRows);
        grammarList.setPrefHeight(240);
        grammarList.setCellFactory(lv -> new GrammarCell());
        grammarList.setId("grammarList");
        Button up = new Button("Up");
        up.setOnAction(e -> moveSelectedGrammar(-1));
        Button down = new Button("Down");
        down.setOnAction(e -> moveSelectedGrammar(1));
        VBox right = new VBox(5,
            DialogParts.bold("File type name:"), nameField, nameError,
            DialogParts.bold("Matching files (regular expression on the file name):"), regexField, regexError,
            DialogParts.bold("Plugins:"), grammarList, new HBox(5, up, down),
            DialogParts.help("Checked plugins are applied to the matching files, in this order."));
        VBox.setVgrow(grammarList, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        HBox main = new HBox(10, left, right);
        VBox.setVgrow(main, Priority.ALWAYS);
        content = DialogParts.page(id(),
            DialogParts.help("A file belongs to every file type it matches: it is parsed with all the plugins"
                + " of these file types (each plugin once, in the order of the list). When two plugins read the"
                + " same text, the first one listed wins; the reading of the other is not kept and gets a warning"
                + " (sections and tags may contain the readings of other plugins)."),
            main);

        if (!fileTypes.isEmpty()) {
            fileTypeList.getSelectionModel().selectFirst();
        } else {
            showFileType(null);
        }
    }

    @Override
    public ProjectPropertiesPage id() {
        return ProjectPropertiesPage.FILE_TYPES;
    }

    @Override
    public Node content() {
        return content;
    }

    @Override
    public void shown() {
        refreshGrammarRows();
    }

    @Override
    public void reveal(ProjectEdit.Problem problem) {
        if (problem.fileType() != null) fileTypeList.getSelectionModel().select(problem.fileType());
        fileTypeList.scrollTo(fileTypeList.getSelectionModel().getSelectedIndex());
        TextField field = problem.field() == ProjectEdit.Field.NAME ? nameField : regexField;
        javafx.application.Platform.runLater(() -> {
            field.requestFocus();
            field.selectAll();
        });
    }

    private void showFileType(FileTypeDraft draft) {
        current = draft;
        loading = true;
        try {
            nameField.setText(draft == null ? "" : draft.getName());
            regexField.setText(draft == null ? "" : draft.getRegex());
            nameField.setDisable(draft == null);
            regexField.setDisable(draft == null);
            grammarList.setDisable(draft == null);
        } finally {
            loading = false;
        }
        showProblems();
        refreshGrammarRows();
    }

    /** The problems of the file type shown, under its fields. */
    private void showProblems() {
        String name = current != null && current.getName().isBlank() ? "The file type has no name" : null;
        String regex = current == null ? null : FileType.checkRegex(current.getRegex());
        show(nameField, nameError, name);
        show(regexField, regexError, regex);
    }

    private static void show(TextField field, Label label, String problem) {
        label.setText(problem == null ? "" : problem);
        label.setVisible(problem != null);
        field.setStyle(problem == null ? "" : "-fx-border-color: " + DialogParts.ERROR_COLOUR + ";");
    }

    /** The grammars of the file type (checked, in rank order), then the other installed grammars. */
    private void refreshGrammarRows() {
        List<GrammarPlugin> rows = new ArrayList<>();
        if (current != null) {
            rows.addAll(current.getGrammars());
            for (GrammarPlugin g : PluginManager.getAvailableGrammars()) {
                if (!current.uses(g)) rows.add(g);
            }
        }
        GrammarPlugin selected = grammarList.getSelectionModel().getSelectedItem();
        grammarRows.setAll(rows);
        if (selected != null) {
            rows.stream().filter(g -> g.getId().equals(selected.getId())).findFirst()
                .ifPresent(g -> grammarList.getSelectionModel().select(g));
        }
    }

    private void moveSelectedGrammar(int direction) {
        GrammarPlugin g = grammarList.getSelectionModel().getSelectedItem();
        if (current != null && g != null && current.uses(g)) {
            current.move(g, direction);
            refreshGrammarRows();
            ctx.changed();
        }
    }

    /**
     * A grammar with a check box (checked = applied to the files of the type, with its rank) and,
     * for a checked one, a "Display…" link to its row of the Plugins tab.
     */
    private class GrammarCell extends ListCell<GrammarPlugin> {
        private final CheckBox check = new CheckBox();
        private final Label label = new Label();
        private final Hyperlink display = new Hyperlink("Display…");
        private final HBox box;

        GrammarCell() {
            // shortened with an ellipsis rather than scrolled sideways
            label.setMinWidth(0);
            label.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(label, Priority.ALWAYS);
            display.setMinWidth(Region.USE_PREF_SIZE);
            display.setTooltip(new Tooltip("Font and colours of this plugin's objects (Plugins tab)"));
            display.managedProperty().bind(display.visibleProperty());
            box = new HBox(10, check, label, display);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setMinWidth(0);
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(GrammarPlugin g, boolean empty) {
            super.updateItem(g, empty);
            if (empty || g == null || current == null) {
                setGraphic(null);
                return;
            }
            boolean used = current.uses(g);
            int rank = current.getGrammars().stream().map(GrammarPlugin::getId).toList().indexOf(g.getId());
            check.setSelected(used);
            check.setOnAction(e -> {
                current.setUsed(g, check.isSelected());
                refreshGrammarRows();
                ctx.changed();
            });
            String text = (used ? (rank + 1) + ". " : "") + g.getDisplayName() + " — " + g.getDisplayDescription();
            label.setText(text);
            label.setTooltip(new Tooltip(text));
            display.setVisible(used);
            display.setOnAction(e -> ctx.showPlugin(g.getId()));
            setGraphic(box);
        }
    }
}
