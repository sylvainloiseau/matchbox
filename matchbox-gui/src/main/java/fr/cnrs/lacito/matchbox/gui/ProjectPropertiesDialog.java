package fr.cnrs.lacito.matchbox.gui;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.model.project.FileType;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.model.project.ProjectEdit;
import fr.cnrs.lacito.matchbox.model.project.ProjectEdit.FileTypeDraft;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Edits the definition of a project: name, description, files and directories, file types (a
 * regular expression on file names and the grammars applied, in rank order), AI assistant.
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

    private final ObservableList<Path> paths = FXCollections.observableArrayList();
    private final ObservableList<FileTypeDraft> fileTypes = FXCollections.observableArrayList();
    private final ObservableList<GrammarPlugin> grammarRows = FXCollections.observableArrayList();
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
        stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(parentStage);
        stage.setTitle("Project properties");
        stage.setWidth(900);
        stage.setHeight(750);

        VBox root = new VBox(10,
            nameSection(), descriptionSection(), filesetSection(), fileTypesSection(), assistantSection(), buttons());
        root.setPadding(new Insets(15));
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        stage.setScene(new Scene(scroll));
        if (!fileTypes.isEmpty()) {
            fileTypeList.getSelectionModel().selectFirst();
        } else {
            showFileType(null);
        }
        stage.showAndWait();
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

    private VBox filesetSection() {
        paths.setAll(edit.getPaths());
        ListView<Path> list = new ListView<>(paths);
        list.setPrefHeight(110);
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Path p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setText(null);
                    setStyle("");
                } else {
                    boolean missing = !Files.exists(p);
                    setText(p + (Files.isDirectory(p) ? "   (directory)" : "") + (missing ? "   (missing)" : ""));
                    setStyle(missing ? "-fx-text-fill: #b00020;" : "");
                }
            }
        });
        Button addFile = new Button("Add file…");
        addFile.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Add a file");
            List<File> files = chooser.showOpenMultipleDialog(stage);
            if (files != null) files.forEach(f -> addPath(f.toPath()));
        });
        Button addDirectory = new Button("Add directory…");
        addDirectory.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Add a directory");
            File d = chooser.showDialog(stage);
            if (d != null) addPath(d.toPath());
        });
        Button remove = new Button("Remove");
        remove.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(e -> {
            Path p = list.getSelectionModel().getSelectedItem();
            paths.remove(p);
            edit.getPaths().remove(p);
        });
        HBox buttons = new HBox(5, addFile, addDirectory, remove);
        buttons.setAlignment(Pos.CENTER_LEFT);
        return section("Fileset", list, buttons);
    }

    private void addPath(Path p) {
        Path absolute = p.toAbsolutePath().normalize();
        if (!edit.getPaths().contains(absolute)) {
            edit.getPaths().add(absolute);
            paths.add(absolute);
        }
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
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            box = new HBox(10, check, label, spacer);
            box.setAlignment(Pos.CENTER_LEFT);
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
            });
            label.setText((used ? (rank + 1) + ". " : "") + g.getDisplayName() + " — " + g.getDisplayDescription());
            setGraphic(box);
        }
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
        List<TextDocument> dropped = edit.droppedDocuments(project);
        if (!UnsavedChangesDialog.confirm(stage, dropped, "removing them from the project")) {
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
