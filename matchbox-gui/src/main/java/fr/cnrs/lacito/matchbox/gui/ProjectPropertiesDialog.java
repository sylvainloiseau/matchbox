package fr.cnrs.lacito.matchbox.gui;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.gui.mode.FileModeController;
import fr.cnrs.lacito.matchbox.model.project.FileType;
import fr.cnrs.lacito.matchbox.model.project.Project;

import java.io.File;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Dialog for editing project properties including name, description, fileset, and grammars
 */
public class ProjectPropertiesDialog {

    private Stage stage;
    private Stage parentStage;
    private Project project;
    private FileModeController fileModeController;
    private boolean saved = false;

    // UI Components
    private TextField projectNameField;
    private TextArea projectDescriptionArea;
    private ListView<String> filesetListView;
    private ObservableList<String> filesetItems;

    private ListView<String> fileSetItemsListView;
    private ObservableList<String> fileSetItems;
    private TextField fileTypeNameField;
    private TextField matchingFilesField;
    private ListView<GrammarItem> grammarListView;
    private ObservableList<GrammarItem> grammarItems;

    private FileType currentPattern;

    public ProjectPropertiesDialog(
        Stage parentStage,
        Project project,
        FileModeController fileModeController
    ) {
        this.parentStage = parentStage;
        this.project = project;
        this.fileModeController = fileModeController;
    }

    public void showAndWait() {
        stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(parentStage);
        stage.setTitle("Project properties");
        stage.setWidth(900);
        stage.setHeight(700);

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        // Section 1: Project name
        VBox nameSection = createNameSection();
        root.getChildren().add(nameSection);

        // Section 2: Project description
        VBox descriptionSection = createDescriptionSection();
        root.getChildren().add(descriptionSection);

        // Section 3: Fileset
        VBox filesetSection = createFilesetSection();
        root.getChildren().add(filesetSection);
        VBox.setVgrow(filesetSection, Priority.SOMETIMES);

        // Section 4: Grammars by file type
        VBox grammarsSection = createGrammarsSection();
        root.getChildren().add(grammarsSection);
        VBox.setVgrow(grammarsSection, Priority.ALWAYS);

        // Bottom buttons
        HBox buttonBox = createButtonBox();
        root.getChildren().add(buttonBox);

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        Scene scene = new Scene(scrollPane);
        stage.setScene(scene);

        stage.showAndWait();
    }

    private VBox createNameSection() {
        VBox section = new VBox(5);
        section.setStyle(
            "-fx-border-color: #cccccc; -fx-border-width: 1; -fx-padding: 10;"
        );

        Label label = new Label("Project name");
        label.setStyle("-fx-font-weight: bold;");

        projectNameField = new TextField();
        projectNameField.setPromptText("Enter project name");

        section.getChildren().addAll(label, projectNameField);
        return section;
    }

    private VBox createDescriptionSection() {
        VBox section = new VBox(5);
        section.setStyle(
            "-fx-border-color: #cccccc; -fx-border-width: 1; -fx-padding: 10;"
        );

        Label label = new Label("Project description");
        label.setStyle("-fx-font-weight: bold;");

        projectDescriptionArea = new TextArea();
        projectDescriptionArea.setPromptText("Enter project description");
        projectDescriptionArea.setWrapText(true);
        projectDescriptionArea.setPrefRowCount(3);

        section.getChildren().addAll(label, projectDescriptionArea);
        return section;
    }

    private VBox createFilesetSection() {
        VBox section = new VBox(5);
        section.setStyle(
            "-fx-border-color: #cccccc; -fx-border-width: 1; -fx-padding: 10;"
        );

        Label label = new Label("Fileset");
        label.setStyle("-fx-font-weight: bold;");

        // Initialize with currently opened files
        filesetItems = FXCollections.observableArrayList();
        if (fileModeController != null) {
            List<String> openFiles = fileModeController.getOpenedFiles();
            if (openFiles != null) {
                filesetItems.addAll(openFiles);
            }
        }

        filesetListView = new ListView<>(filesetItems);
        filesetListView.setPrefHeight(100);
        filesetListView.setStyle("-fx-control-inner-background: #f5f5f5;");

        HBox buttonBox = new HBox(5);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        Button addButton = new Button("Add");
        addButton.setOnAction(e -> handleAddFile());

        Button removeButton = new Button("Remove");
        removeButton.setOnAction(e -> handleRemoveFile());

        buttonBox.getChildren().addAll(addButton, removeButton);

        section.getChildren().addAll(label, filesetListView, buttonBox);
        return section;
    }

    private VBox createGrammarsSection() {
        VBox section = new VBox(5);
        section.setStyle(
            "-fx-border-color: #cccccc; -fx-border-width: 1; -fx-padding: 10;"
        );

        Label label = new Label("Define file sets with file patterns and select appopriate grammars");
        label.setStyle("-fx-font-weight: bold;");

        // Main horizontal split: left side (file types list) and right side (details)
        HBox mainBox = new HBox(10);
        VBox.setVgrow(mainBox, Priority.ALWAYS);

        // Left side: File type list
        VBox leftBox = createFileSetListSection();
        mainBox.getChildren().add(leftBox);
        HBox.setHgrow(leftBox, Priority.ALWAYS);

        // Right side: File type details
        VBox rightBox = createFileSetDetailsSection();
        mainBox.getChildren().add(rightBox);
        HBox.setHgrow(rightBox, Priority.ALWAYS);

        section.getChildren().addAll(label, mainBox);
        VBox.setVgrow(mainBox, Priority.ALWAYS);
        return section;
    }

    private VBox createFileSetListSection() {
        VBox box = new VBox(5);
        box.setStyle(
            "-fx-border-color: #e0e0e0; -fx-border-width: 1; -fx-padding: 5;"
        );

        Label title = new Label("File sets:");
        title.setStyle("-fx-font-weight: bold;");

        fileSetItems = FXCollections.observableArrayList();
        fileSetItemsListView = new ListView<>(fileSetItems);
        fileSetItemsListView.setPrefHeight(200);
        fileSetItemsListView.setStyle("-fx-control-inner-background: #f5f5f5;");

        fileSetItemsListView
            .getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    // Find the corresponding pattern
                    for (FileType pattern : project.getFileTypes()) {
                        if (pattern.getName().equals(newVal)) {
                            currentPattern = pattern;
                            updateDetailsPanel();
                            break;
                        }
                    }
                }
            });

        HBox buttonBox = new HBox(5);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        Button addButton = new Button("Add");
        addButton.setOnAction(e -> handleAddFileType());

        Button removeButton = new Button("Remove");
        removeButton.setOnAction(e -> handleRemoveFileType());

        buttonBox.getChildren().addAll(addButton, removeButton);

        box.getChildren().addAll(title, fileSetItemsListView, buttonBox);
        VBox.setVgrow(fileSetItemsListView, Priority.ALWAYS);
        return box;
    }

    private VBox createFileSetDetailsSection() {
        VBox box = new VBox(5);
        box.setStyle(
            "-fx-border-color: #e0e0e0; -fx-border-width: 1; -fx-padding: 5;"
        );
        Label title = new Label("File set detail:");
        title.setStyle("-fx-font-weight: bold;");

        // File type name field
        Label nameLabel = new Label("File type name:");
        nameLabel.setStyle("-fx-font-weight: bold;");
        fileTypeNameField = new TextField();
        fileTypeNameField.setPromptText("e.g., 'Conll files', 'Old fieldwork (with simple lexical pattern)'");
        fileTypeNameField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (currentPattern != null) {
                currentPattern.setName(newVal);
                // Update the list view
                int selectedIndex = fileSetItemsListView
                    .getSelectionModel()
                    .getSelectedIndex();
                fileSetItems.set(selectedIndex, newVal);
            }
        });

        // Matching files regex field
        Label regexLabel = new Label("Matching files (regex):");
        regexLabel.setStyle("-fx-font-weight: bold;");
        matchingFilesField = new TextField();
        matchingFilesField.setPromptText("e.g., .*\\\\.md$");
        matchingFilesField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (currentPattern != null) {
                currentPattern.setRegex(newVal);
            }
        });

        // Grammars list
        Label grammarsLabel = new Label("Grammars:");
        grammarsLabel.setStyle("-fx-font-weight: bold;");

        grammarItems = FXCollections.observableArrayList();
        grammarListView = new ListView<>(grammarItems);
        grammarListView.setPrefHeight(150);
        grammarListView.setCellFactory(param -> new GrammarListCell());
        grammarListView.setStyle("-fx-control-inner-background: #f5f5f5;");

        // Enable drag and drop reordering
        grammarListView.setOnDragOver(event -> {
            if (
                event.getGestureSource() != grammarListView &&
                event.getDragboard().hasString()
            ) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        grammarListView.setOnDragDropped(event -> {
            if (event.getGestureSource() == grammarListView) {
                int draggedIdx = grammarListView
                    .getSelectionModel()
                    .getSelectedIndex();
                if (draggedIdx >= 0) {
                    int dropIdx = grammarListView
                        .getSelectionModel()
                        .getSelectedIndex();
                    if (dropIdx >= 0 && draggedIdx != dropIdx) {
                        GrammarItem item = grammarItems.remove(draggedIdx);
                        grammarItems.add(dropIdx, item);
                        // Update ranks in the pattern
                        if (currentPattern != null) {
                            for (
                                int i = 0;
                                i < currentPattern.getGrammars().size();
                                i++
                            ) {
                                //currentPattern.getGrammars().get(i).setRank(i);
                            }
                        }
                    }
                }
                event.setDropCompleted(true);
            }
            event.consume();
        });

        VBox.setVgrow(grammarListView, Priority.ALWAYS);

        box.getChildren().addAll(
            nameLabel,
            fileTypeNameField,
            regexLabel,
            matchingFilesField,
            grammarsLabel,
            grammarListView
        );

        return box;
    }

    private HBox createButtonBox() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_RIGHT);

        Button saveButton = new Button("Save");
        saveButton.setPrefWidth(80);
        saveButton.setOnAction(e -> handleSave());

        Button cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(80);
        cancelButton.setOnAction(e -> handleCancel());

        box.getChildren().addAll(saveButton, cancelButton);
        return box;
    }

    private void updateDetailsPanel() {
        if (currentPattern != null) {
            fileTypeNameField.setText(currentPattern.getName());
            matchingFilesField.setText(currentPattern.getRegex());

            // Load grammars for this pattern
            grammarItems.clear();
            for (GrammarPlugin grammar : currentPattern.getGrammars()) {
                grammarItems.add(new GrammarItem(grammar));
            }
        } else {
            fileTypeNameField.setText("");
            matchingFilesField.setText("");
            grammarItems.clear();
        }
    }

    private void handleAddFile() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select File or Directory");
        File selectedDir = dirChooser.showDialog(stage);
        if (
            selectedDir != null &&
            !filesetItems.contains(selectedDir.getAbsolutePath())
        ) {
            filesetItems.add(selectedDir.getAbsolutePath());
            project.addDirectory(selectedDir.getAbsolutePath());
        }
    }

    private void handleRemoveFile() {
        int selectedIndex = filesetListView
            .getSelectionModel()
            .getSelectedIndex();
        if (selectedIndex >= 0) {
            String removed = filesetItems.remove(selectedIndex);
            project.removeDirectory(removed);
            project.removeFile(removed);
        }
    }

    private void handleAddFileType() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add File Type");
        dialog.setHeaderText("Enter file type name");
        dialog.setContentText("File type name:");
        dialog.initOwner(stage);

        if (dialog.showAndWait().isPresent()) {
            String fileTypeName = dialog.getResult();
            if (!fileTypeName.isEmpty()) {
                FileType pattern = new FileType(
                    fileTypeName,
                    ""
                );

                // Add default grammars
                for (GrammarPlugin grammar : PluginManager.getAvailableGrammars()) {
                    pattern.addGrammar(grammar);
                }

                project.addFileType(pattern);
                fileSetItems.add(fileTypeName);
                fileSetItemsListView.getSelectionModel().selectLast();
                currentPattern = pattern;
            }
        }
    }

    private void handleRemoveFileType() {
        int selectedIndex = fileSetItemsListView
            .getSelectionModel()
            .getSelectedIndex();
        if (selectedIndex >= 0) {
            String removed = fileSetItems.remove(selectedIndex);
            project.removeFileType(currentPattern);
            currentPattern = null;
            updateDetailsPanel();
        }
    }

    private void handleSave() {
        project.setName(projectNameField.getText());
        project.setDescription(projectDescriptionArea.getText());

        // Update fileset
        project.getFiles().clear();
        project.getDirectories().clear();
        for (String item : filesetItems) {
            File f = new File(item);
            if (f.isFile()) {
                project.addFile(item);
            } else if (f.isDirectory()) {
                project.addDirectory(item);
            }
        }

        // Update current pattern if edited
        if (currentPattern != null) {
            currentPattern.setName(fileTypeNameField.getText());
            currentPattern.setRegex(matchingFilesField.getText());

            // Update grammars from UI
            currentPattern.getGrammars().clear();
            for (GrammarItem item : grammarItems) {
                currentPattern.addGrammar(item.getGrammar());
            }
        }
        saved = true;
        stage.close();
    }

    private void handleCancel() {
        saved = false;
        stage.close();
    }

    public boolean isSaved() {
        return saved;
    }

    /**
     * Wrapper class for displaying Grammar with checkbox
     */
    private static class GrammarItem {

        private GrammarPlugin grammar;

        GrammarItem(GrammarPlugin grammar) {
            this.grammar = grammar;
        }

        GrammarPlugin getGrammar() {
            return grammar;
        }

        @Override
        public String toString() {
            return grammar.getDisplayName() + " - " + grammar.getDisplayDescription();
        }
    }

    /**
     * Custom ListCell for displaying Grammar items with checkbox
     */
    private class GrammarListCell extends ListCell<GrammarItem> {

        private HBox container;
        private CheckBox checkBox;
        private Label label;

        GrammarListCell() {
            container = new HBox(10);
            container.setPadding(new Insets(5));
            container.setAlignment(Pos.CENTER_LEFT);

            checkBox = new CheckBox();
            label = new Label();
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            container.getChildren().addAll(checkBox, label, spacer);
        }

        @Override
        protected void updateItem(GrammarItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
            } else {
                GrammarPlugin grammar = item.getGrammar();
                label.setText(
                    grammar.getDisplayName() + " - " + grammar.getDisplayDescription()
                );
                checkBox.setSelected(true);

                // Update grammar selection when checkbox is toggled
                checkBox.setOnAction(e -> {
                    if (currentPattern != null) {
                        // Update rank if selected
                        if (checkBox.isSelected()) {
                            int maxRank = 0;
                            for (GrammarPlugin g : currentPattern.getGrammars()) {
                                // if (g.isSelected() && g.getRank() > maxRank) {
                                //     maxRank = g.getRank();
                                // }
                            }
                            //grammar.setRank(maxRank + 1);
                        }
                    }
                });
                setGraphic(container);
            }
        }
    }
}
