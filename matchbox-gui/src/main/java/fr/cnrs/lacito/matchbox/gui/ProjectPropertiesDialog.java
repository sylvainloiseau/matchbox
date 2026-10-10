package fr.cnrs.lacito.matchbox.gui;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import fr.cnrs.lacito.matchbox.gui.view.DisplayStyleCss;
import fr.cnrs.lacito.matchbox.gui.view.HighlightStyles;
import fr.cnrs.lacito.matchbox.gui.view.KindCatalog;
import fr.cnrs.lacito.matchbox.model.project.DisplayStyles;
import fr.cnrs.lacito.matchbox.model.project.FileType;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.model.project.ProjectEdit;
import fr.cnrs.lacito.matchbox.model.project.ProjectEdit.FileTypeDraft;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Edits the definition of a project: name, description, file types (a regular expression on file
 * names and the grammars applied, in rank order), plugin parameters (display styles, shared by all
 * the file types), AI assistant. Files and directories are added with the Current project menu and
 * listed in File Mode.
 *
 * <p>The dialog edits a {@link ProjectEdit} working copy: the project is changed only by Save
 * (documents whose grammars changed are then parsed again); Cancel discards everything.
 */
public class ProjectPropertiesDialog {

    private static final String SECTION = "-fx-border-color: #cccccc; -fx-border-width: 1; -fx-padding: 10;";

    private final Stage parentStage;
    private final Project project;
    private final ProjectEdit edit;
    private Stage stage;
    private boolean saved = false;

    private final ObservableList<FileTypeDraft> fileTypes = FXCollections.observableArrayList();
    private final ObservableList<GrammarPlugin> grammarRows = FXCollections.observableArrayList();
    private final ObservableList<PluginRow> pluginRows = FXCollections.observableArrayList();
    private ListView<PluginRow> pluginList;
    private ListView<FileTypeDraft> fileTypeList;
    private ListView<GrammarPlugin> grammarList;
    private TextField fileTypeNameField;
    private TextField regexField;
    private final Label regexErrorLabel = new Label();
    private FileTypeDraft current;
    /** True while the detail fields are filled from a draft (their listeners must not write back). */
    private boolean loading;

    public ProjectPropertiesDialog(Stage parentStage, Project project) {
        this.parentStage = parentStage;
        this.project = project;
        this.edit = ProjectEdit.of(project);
    }

    public void showAndWait() {
        build(true).showAndWait();
    }

    /** Shows the dialog without waiting and without blocking its owner (for the GUI tests). */
    Stage show() {
        Stage s = build(false);
        s.show();
        return s;
    }

    private Stage build(boolean modal) {
        stage = new Stage();
        if (modal) stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(parentStage);
        stage.setTitle("Project properties");
        stage.setWidth(900);
        stage.setHeight(750);

        VBox root = new VBox(10,
            nameSection(), descriptionSection(), fileTypesSection(), pluginParametersSection(), assistantSection(),
            buttons());
        root.setPadding(new Insets(15));
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        stage.setScene(new Scene(scroll));
        if (!fileTypes.isEmpty()) {
            fileTypeList.getSelectionModel().selectFirst();
        } else {
            showFileType(null);
        }
        refreshPluginRows();
        return stage;
    }

    public boolean isSaved() {
        return saved;
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private static VBox section(String title, javafx.scene.Node... content) {
        Label label = new Label(title);
        label.setStyle("-fx-font-weight: bold;");
        VBox box = new VBox(5, label);
        box.getChildren().addAll(content);
        box.setStyle(SECTION);
        return box;
    }

    private VBox nameSection() {
        TextField name = new TextField(edit.getName());
        name.setPromptText("Enter project name");
        name.textProperty().addListener((obs, o, n) -> edit.setName(n));
        return section("Project name", name);
    }

    private VBox descriptionSection() {
        TextArea description = new TextArea(edit.getDescription());
        description.setPromptText("Enter project description");
        description.setWrapText(true);
        description.setPrefRowCount(3);
        description.textProperty().addListener((obs, o, n) -> edit.setDescription(n));
        return section("Project description", description);
    }

    private VBox fileTypesSection() {
        fileTypes.setAll(edit.getFileTypes());
        fileTypeList = new ListView<>(fileTypes);
        fileTypeList.setPrefHeight(200);
        fileTypeList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> showFileType(n));
        Button add = new Button("Add");
        add.setOnAction(e -> {
            FileTypeDraft draft = new FileTypeDraft("New file type", ".+", List.of());
            edit.getFileTypes().add(draft);
            fileTypes.add(draft);
            refreshPluginRows();
            fileTypeList.getSelectionModel().select(draft);
            fileTypeNameField.requestFocus();
            fileTypeNameField.selectAll();
        });
        Button remove = new Button("Remove");
        remove.disableProperty().bind(fileTypeList.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(e -> {
            FileTypeDraft draft = fileTypeList.getSelectionModel().getSelectedItem();
            edit.getFileTypes().remove(draft);
            fileTypes.remove(draft);
            refreshPluginRows();
        });
        VBox left = new VBox(5, new Label("File types:"), fileTypeList, new HBox(5, add, remove));
        VBox.setVgrow(fileTypeList, Priority.ALWAYS);
        HBox.setHgrow(left, Priority.ALWAYS);

        fileTypeNameField = new TextField();
        fileTypeNameField.setPromptText("e.g. 'Conll files', 'Old fieldwork'");
        fileTypeNameField.textProperty().addListener((obs, o, n) -> {
            if (current != null && !loading) {
                current.setName(n);
                fileTypeList.refresh();
            }
        });
        regexField = new TextField();
        regexField.setPromptText("e.g. .*\\.md");
        regexErrorLabel.setWrapText(true);
        regexErrorLabel.setStyle("-fx-text-fill: #b00020;");
        regexErrorLabel.managedProperty().bind(regexErrorLabel.visibleProperty());
        regexField.textProperty().addListener((obs, o, n) -> {
            if (current != null && !loading) current.setRegex(n);
            showRegexProblem(current == null ? null : FileType.checkRegex(n));
        });

        grammarList = new ListView<>(grammarRows);
        grammarList.setPrefHeight(150);
        grammarList.setCellFactory(lv -> new GrammarCell());
        grammarList.setId("grammarList");
        Button up = new Button("Up");
        up.setOnAction(e -> moveSelectedGrammar(-1));
        Button down = new Button("Down");
        down.setOnAction(e -> moveSelectedGrammar(1));
        Label help = new Label("Checked grammars are applied to the matching files, in this order.");
        help.setStyle("-fx-text-fill: #666666;");
        VBox right = new VBox(5,
            bold("File type name:"), fileTypeNameField,
            bold("Matching files (regular expression on the file name):"), regexField, regexErrorLabel,
            bold("Grammars:"), grammarList, new HBox(5, up, down), help);
        VBox.setVgrow(grammarList, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        HBox main = new HBox(10, left, right);
        VBox box = section("File types: patterns of file names and the grammars applied to them", main);
        VBox.setVgrow(main, Priority.ALWAYS);
        return box;
    }

    private static Label bold(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    // ------------------------------------------------------------------
    // Plugin parameters
    // ------------------------------------------------------------------

    /** A plugin of the Plugin parameters section; {@code plugin} is null if it is not installed. */
    private record PluginRow(String grammarId, GrammarPlugin plugin) {}

    private VBox pluginParametersSection() {
        pluginList = new ListView<>(pluginRows);
        pluginList.setId("pluginParameters");
        pluginList.setPrefHeight(170);
        pluginList.setCellFactory(lv -> new PluginCell());
        Label help = new Label("The display of a plugin is the same in every file type using it. "
            + "Check a plugin in a file type to edit its display.");
        help.setWrapText(true);
        help.setStyle("-fx-text-fill: #666666;");
        return section("Plugin parameters", pluginList, help);
    }

    /**
     * Installed plugins, those used by a file type first (order of first use), then the others by
     * name; then the plugins that are not installed but have display styles in the project.
     */
    private void refreshPluginRows() {
        if (pluginList == null) return;
        List<GrammarPlugin> installed = PluginManager.getAvailableGrammars();
        List<String> used = edit.usedGrammarIds();
        List<PluginRow> rows = new ArrayList<>();
        for (String id : used) {
            installed.stream().filter(g -> g.getId().equals(id)).findFirst()
                .ifPresent(g -> rows.add(new PluginRow(id, g)));
        }
        installed.stream().filter(g -> !used.contains(g.getId()))
            .sorted(java.util.Comparator.comparing(GrammarPlugin::getDisplayName, String.CASE_INSENSITIVE_ORDER))
            .forEach(g -> rows.add(new PluginRow(g.getId(), g)));
        edit.getDisplayStyles().asMap().keySet().stream().map(DisplayStyles.Key::grammarId).distinct()
            .filter(id -> installed.stream().noneMatch(g -> g.getId().equals(id)))
            .sorted()
            .forEach(id -> rows.add(new PluginRow(id, null)));
        pluginRows.setAll(rows);
    }

    /**
     * Name and description (shortened with an ellipsis), a sample of the plugin's display, the file
     * types using it and, right aligned, Display… (enabled when the plugin is used) or Forget (a
     * plugin that is not installed).
     */
    private class PluginCell extends ListCell<PluginRow> {
        private final Label name = new Label();
        private final Label description = new Label();
        private final Label swatch = new Label("Aa");
        private final Label usedIn = new Label();
        private final Button button = new Button();
        private final HBox box;

        PluginCell() {
            name.setStyle("-fx-font-weight: bold;");
            name.setMinWidth(Region.USE_PREF_SIZE);
            description.setStyle("-fx-text-fill: #555555;");
            description.setMinWidth(0);
            description.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(description, Priority.ALWAYS);
            swatch.setMinWidth(Region.USE_PREF_SIZE);
            usedIn.setMinWidth(0);
            usedIn.setPrefWidth(150);
            usedIn.setMaxWidth(150);
            button.setMinWidth(Region.USE_PREF_SIZE);
            box = new HBox(10, name, description, swatch, usedIn, button);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setMinWidth(0);
            // no horizontal scrolling: the cell is as wide as the list
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(PluginRow row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setGraphic(null);
                return;
            }
            DisplayStyle style = edit.getDisplayStyles().get(DisplayStyles.Key.grammar(row.grammarId()));
            if (row.plugin() == null) {
                name.setText(row.grammarId());
                description.setText("(not installed)");
                swatch.setStyle("-fx-padding: 0 4 0 4;" + DisplayStyleCss.label(style));
                usedIn.setText("");
                usedIn.setTooltip(null);
                button.setText("Forget");
                button.setDisable(false);
                button.setTooltip(new Tooltip("Remove the display styles of this plugin from the project"));
                button.setOnAction(e -> forgetStyles(row.grammarId()));
                description.setTooltip(null);
                swatch.setTooltip(null);
                name.setStyle("-fx-font-weight: bold; -fx-text-fill: #888888;");
            } else {
                GrammarPlugin g = row.plugin();
                name.setText(g.getDisplayName());
                name.setStyle("-fx-font-weight: bold;");
                description.setText(g.getDisplayDescription());
                description.setTooltip(new Tooltip(g.getDisplayName() + " — " + g.getDisplayDescription()));
                swatch.setStyle("-fx-padding: 0 4 0 4;"
                    + DisplayStyleCss.label(style.over(HighlightStyles.defaultStyle(g, null))));
                swatch.setTooltip(new Tooltip("Display of all the objects of this plugin; some kinds may have their own"));
                List<String> types = edit.fileTypesUsing(g.getId());
                usedIn.setText(types.isEmpty() ? "not used" : String.join(", ", types));
                usedIn.setStyle(types.isEmpty() ? "-fx-text-fill: #888888;" : "");
                usedIn.setTooltip(new Tooltip(types.isEmpty() ? "Checked in no file type"
                    : "Checked in: " + String.join(", ", types)));
                button.setText("Display…");
                button.setDisable(types.isEmpty());
                button.setTooltip(new Tooltip(types.isEmpty() ? "Check this plugin in a file type first"
                    : "Font and colours of the objects of this plugin in the editor"));
                button.setOnAction(e -> editDisplay(g));
            }
            setGraphic(box);
        }
    }

    private void forgetStyles(String grammarId) {
        DisplayStyles styles = edit.getDisplayStyles();
        for (DisplayStyles.Key key : List.copyOf(styles.asMap().keySet())) {
            if (key.grammarId().equals(grammarId)) styles = styles.with(key, DisplayStyle.EMPTY);
        }
        edit.setDisplayStyles(styles);
        refreshPluginRows();
    }

    private VBox assistantSection() {
        CheckBox allow = new CheckBox("Allow cloud AI providers for this project");
        allow.setSelected(edit.isCloudAssistAllowed());
        allow.selectedProperty().addListener((obs, o, n) -> edit.setCloudAssistAllowed(n));
        Label help = new Label(
            "Uncheck if the notes must not leave this computer (e.g. personal data of consultants): " +
            "only a local model (Ollama) can then be used for this project."
        );
        help.setWrapText(true);
        help.setStyle("-fx-text-fill: #666666;");
        return section("AI assistant", allow, help);
    }

    private HBox buttons() {
        Button save = new Button("Save");
        save.setPrefWidth(80);
        save.setDefaultButton(false);
        save.setOnAction(e -> handleSave());
        Button cancel = new Button("Cancel");
        cancel.setPrefWidth(80);
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> stage.close());
        HBox box = new HBox(10, save, cancel);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    // ------------------------------------------------------------------
    // File type details
    // ------------------------------------------------------------------

    private void showFileType(FileTypeDraft draft) {
        current = draft;
        loading = true;
        try {
            fileTypeNameField.setText(draft == null ? "" : draft.getName());
            regexField.setText(draft == null ? "" : draft.getRegex());
            fileTypeNameField.setDisable(draft == null);
            regexField.setDisable(draft == null);
            grammarList.setDisable(draft == null);
        } finally {
            loading = false;
        }
        showRegexProblem(draft == null ? null : FileType.checkRegex(draft.getRegex()));
        refreshGrammarRows();
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
        }
    }

    private void showRegexProblem(String problem) {
        regexErrorLabel.setText(problem == null ? "" : problem);
        regexErrorLabel.setVisible(problem != null);
        regexField.setStyle(problem == null ? "" : "-fx-border-color: #b00020;");
    }

    /** A grammar with a check box: checked = applied to the files of the type; its rank is shown. */
    private class GrammarCell extends ListCell<GrammarPlugin> {
        private final CheckBox check = new CheckBox();
        private final Label label = new Label();
        private final HBox box;

        GrammarCell() {
            // shortened with an ellipsis rather than scrolled sideways
            label.setMinWidth(0);
            label.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(label, Priority.ALWAYS);
            box = new HBox(10, check, label);
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
                refreshPluginRows();
            });
            String text = (used ? (rank + 1) + ". " : "") + g.getDisplayName() + " — " + g.getDisplayDescription();
            label.setText(text);
            label.setTooltip(new Tooltip(text));
            setGraphic(box);
        }
    }

    /** Edits the display styles of a grammar in the working copy (applied by Save). */
    private void editDisplay(GrammarPlugin g) {
        List<String> declared;
        try {
            declared = g.getObjectKinds();
        } catch (RuntimeException | LinkageError e) {
            declared = List.of();
        }
        List<String> kinds = KindCatalog.kinds(g.getId(), declared, project.getCorpus(), edit.getDisplayStyles());
        new DisplayStyleDialog(g, kinds, edit.getDisplayStyles()).showAndWait(stage)
            .ifPresent(styles -> {
                edit.setDisplayStyles(styles);
                refreshPluginRows();
            });
    }

    // ------------------------------------------------------------------
    // Save
    // ------------------------------------------------------------------

    private void handleSave() {
        List<String> problems = edit.problems();
        if (!problems.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, String.join("\n", problems));
            alert.initOwner(stage);
            alert.setTitle("Project properties");
            alert.setHeaderText("The project cannot be saved");
            alert.showAndWait();
            return;
        }
        ProjectEdit.Result result = edit.applyTo(project);
        if (!result.problems().isEmpty()) {
            ErrorReporter.report(stage, "Some files could not be added", String.join("\n", result.problems()));
        }
        saved = true;
        stage.close();
    }
}
