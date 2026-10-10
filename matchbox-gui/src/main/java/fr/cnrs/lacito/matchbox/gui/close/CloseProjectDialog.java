package fr.cnrs.lacito.matchbox.gui.close;

import fr.cnrs.lacito.matchbox.model.TextDocument;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * The question asked before a project with unsaved changes is closed: a yes/no question if only
 * the definition changed; otherwise the list of the modified files with a check box each (all
 * checked), and a "Yes" check box for the definition if it changed too.
 */
public final class CloseProjectDialog {

    static final String DEFINITION_CHANGED =
        "The project definition (files, file types, plugins, display) has changed.";

    private CloseProjectDialog() {
    }

    /** An {@link ProjectCloser.Asker} showing this dialog over {@code owner}. */
    public static ProjectCloser.Asker asker(Window owner, Optional<Path> projectDirectory) {
        return (state, action) -> ask(owner, state, action, projectDirectory);
    }

    public static Optional<CloseChoice> ask(Window owner, UnsavedProjectState state, CloseAction action,
                                            Optional<Path> projectDirectory) {
        return state.definitionOnly() ? askDefinition(owner, action) : askFiles(owner, state, action, projectDirectory);
    }

    private static Optional<CloseChoice> askDefinition(Window owner, CloseAction action) {
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Don't save", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", save, discard, cancel);
        if (owner != null) alert.initOwner(owner);
        alert.setTitle("Unsaved project");
        alert.setHeaderText(DEFINITION_CHANGED);
        alert.setContentText("Would you like to save the changes before " + action.gerund() + "?");
        Optional<ButtonType> answer = alert.showAndWait();
        if (answer.isEmpty() || answer.get() == cancel) return Optional.empty();
        return Optional.of(new CloseChoice(answer.get() == save, List.of()));
    }

    private static Optional<CloseChoice> askFiles(Window owner, UnsavedProjectState state, CloseAction action,
                                                  Optional<Path> projectDirectory) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Unsaved changes");
        dialog.setHeaderText("Save the changes before " + action.gerund() + "?");

        VBox content = new VBox(8);
        CheckBox saveDefinition = new CheckBox("Yes");
        saveDefinition.setSelected(true);
        if (state.definitionChanged()) {
            Label definition = new Label(DEFINITION_CHANGED + " Would you like to save the changes?");
            definition.setWrapText(true);
            content.getChildren().addAll(definition, saveDefinition);
        }

        Map<TextDocument, BooleanProperty> selected = new LinkedHashMap<>();
        for (TextDocument d : state.dirty()) selected.put(d, new SimpleBooleanProperty(true));
        Label filesLabel = new Label("The following files have changed. Select the files you want to save:");
        filesLabel.setWrapText(true);
        ListView<TextDocument> files = new ListView<>(FXCollections.observableArrayList(state.dirty()));
        files.setId("unsavedFilesList");
        files.setPrefHeight(Math.min(240, 26 * state.dirty().size() + 4));
        StringConverter<TextDocument> names = new StringConverter<>() {
            @Override
            public String toString(TextDocument d) {
                return d == null ? "" : displayPath(d.getFilePath(), projectDirectory);
            }

            @Override
            public TextDocument fromString(String s) {
                return null;
            }
        };
        files.setCellFactory(lv -> {
            ListCell<TextDocument> cell = new CheckBoxListCell<>(selected::get, names);
            cell.itemProperty().addListener((obs, o, d) ->
                cell.setTooltip(d == null ? null : new Tooltip(d.getFilePath().toString())));
            return cell;
        });
        Button selectAll = new Button("Select all");
        selectAll.setOnAction(e -> selected.values().forEach(p -> p.set(true)));
        Button unselectAll = new Button("Unselect all");
        unselectAll.setOnAction(e -> selected.values().forEach(p -> p.set(false)));
        content.getChildren().addAll(filesLabel, files, new HBox(6, selectAll, unselectAll));
        content.setPrefWidth(520);
        dialog.getDialogPane().setContent(content);

        ButtonType proceed = new ButtonType("Save selected and " + action.verb(), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(proceed, cancel);
        // "Close without saving" when nothing is checked
        BooleanBinding nothing = Bindings.createBooleanBinding(
            () -> selected.values().stream().noneMatch(BooleanProperty::get)
                && !(state.definitionChanged() && saveDefinition.isSelected()),
            Stream.concat(selected.values().stream(), Stream.of(saveDefinition.selectedProperty()))
                .toArray(javafx.beans.Observable[]::new));
        Button proceedButton = (Button) dialog.getDialogPane().lookupButton(proceed);
        String verb = Character.toUpperCase(action.verb().charAt(0)) + action.verb().substring(1);
        proceedButton.textProperty().bind(Bindings.when(nothing)
            .then(verb + " without saving").otherwise("Save selected and " + action.verb()));

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != proceed) return Optional.empty();
        List<TextDocument> toSave = selected.entrySet().stream().filter(e -> e.getValue().get()).map(Map.Entry::getKey).toList();
        return Optional.of(new CloseChoice(state.definitionChanged() && saveDefinition.isSelected(), toSave));
    }

    /** The path relative to the project directory when the file is inside it. */
    static String displayPath(Path file, Optional<Path> projectDirectory) {
        Path absolute = file.toAbsolutePath().normalize();
        return projectDirectory.map(d -> d.toAbsolutePath().normalize())
            .filter(absolute::startsWith)
            .map(d -> d.relativize(absolute).toString())
            .orElse(absolute.toString());
    }

}
