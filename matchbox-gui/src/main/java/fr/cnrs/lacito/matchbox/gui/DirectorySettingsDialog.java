package fr.cnrs.lacito.matchbox.gui;

import fr.cnrs.lacito.matchbox.model.project.DirectorySettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Edits the settings of a directory of the project: glob patterns of the files to include and to
 * exclude, and whether its sub-directories are included, with a preview of the files it gives
 * (computed in the background, large directories may be slow to walk).
 */
public final class DirectorySettingsDialog {

    /**
     * What new settings change: the files the directory will give, those added and removed
     * compared with the files it gives now (relative paths, "/" separators).
     */
    public record Preview(int total, List<String> added, List<String> removed, List<String> kept) {

        public static Preview of(Path directory, List<Path> before, List<Path> after) {
            List<String> added = new ArrayList<>();
            List<String> kept = new ArrayList<>();
            for (Path p : after) {
                (before.contains(p) ? kept : added).add(DirectorySettings.relativePath(directory, p));
            }
            List<String> removed = before.stream().filter(p -> !after.contains(p))
                .map(p -> DirectorySettings.relativePath(directory, p)).toList();
            return new Preview(after.size(), added, removed, kept);
        }

        /** "14 files (2 added, 1 removed)". */
        public String summary() {
            String s = total + (total == 1 ? " file" : " files");
            List<String> changes = new ArrayList<>();
            if (!added.isEmpty()) changes.add(added.size() + " added");
            if (!removed.isEmpty()) changes.add(removed.size() + " removed");
            return changes.isEmpty() ? s : s + " (" + String.join(", ", changes) + ")";
        }

        /** Lines of the list: added (+) and removed (−) first, then the others. */
        public List<String> lines() {
            List<String> lines = new ArrayList<>();
            added.forEach(p -> lines.add("+ " + p));
            removed.forEach(p -> lines.add("− " + p));
            kept.forEach(p -> lines.add("   " + p));
            return lines;
        }
    }

    private DirectorySettingsDialog() {
    }

    /**
     * @param current the files the directory gives now (empty for a directory being added)
     * @param adding  true when the directory is being added (title and OK button say so)
     * @return the new settings; empty on Cancel
     */
    public static Optional<DirectorySettings> show(Window owner, Path directory, DirectorySettings initial,
                                                   List<Path> current, boolean adding) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle(adding ? "Add directory" : "Directory settings");
        dialog.setHeaderText((adding ? "Add the files of " : "Files of ") + directory);
        dialog.setResizable(true);

        TextArea includes = new TextArea(String.join("\n", initial.includes()));
        includes.setId("directoryIncludes");
        includes.setPrefRowCount(3);
        includes.setPromptText("Every file (no pattern)");
        TextArea excludes = new TextArea(String.join("\n", initial.excludes()));
        excludes.setId("directoryExcludes");
        excludes.setPrefRowCount(3);
        excludes.setPromptText("No file excluded");
        CheckBox recursive = new CheckBox("Recursively include sub-directories");
        recursive.setId("directoryRecursive");
        recursive.setSelected(initial.recursive());
        Label help = new Label("One glob per line. *.txt: any .txt file, at any depth · drafts/**: everything under "
            + "drafts · a pattern without \"/\" matches the file name, one with \"/\" the path inside the directory · "
            + "case is ignored · excludes win over includes.");
        help.setWrapText(true);
        help.setStyle("-fx-text-fill: #666666;");
        Label problems = new Label();
        problems.setId("directoryProblems");
        problems.setWrapText(true);
        problems.setStyle("-fx-text-fill: #b00020;");
        problems.managedProperty().bind(problems.visibleProperty());
        Label summary = new Label();
        summary.setId("directoryPreviewSummary");
        summary.setStyle("-fx-font-weight: bold;");
        ListView<String> preview = new ListView<>();
        preview.setId("directoryPreview");
        preview.setPrefHeight(180);

        VBox content = new VBox(6,
            new Label("Include files matching:"), includes,
            new Label("Exclude files matching:"), excludes,
            recursive, help, problems, summary, preview);
        content.setPrefWidth(560);
        dialog.getDialogPane().setContent(content);
        ButtonType ok = new ButtonType(adding ? "Add" : "OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ok, ButtonType.CANCEL);
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ok);

        // the preview follows the edits, 300 ms after the last one, computed off the JavaFX thread
        AtomicInteger generation = new AtomicInteger();
        PauseTransition debounce = new PauseTransition(Duration.millis(300));
        Runnable update = () -> {
            DirectorySettings settings = settings(includes, excludes, recursive);
            List<String> found = settings.problems();
            problems.setText(String.join("\n", found));
            problems.setVisible(!found.isEmpty());
            okButton.setDisable(!found.isEmpty());
            if (!found.isEmpty()) {
                summary.setText("");
                preview.getItems().clear();
                return;
            }
            int mine = generation.incrementAndGet();
            summary.setText("Looking for the files…");
            Thread worker = new Thread(() -> {
                String text;
                List<String> lines;
                try {
                    Preview p = Preview.of(directory, current, settings.scan(directory));
                    text = "Preview: " + p.summary();
                    lines = p.lines();
                } catch (Exception e) {
                    text = "The directory cannot be read: " + e.getMessage();
                    lines = List.of();
                }
                String t = text;
                List<String> l = lines;
                Platform.runLater(() -> {
                    if (generation.get() != mine) return;
                    summary.setText(t);
                    preview.setItems(FXCollections.observableArrayList(l));
                });
            }, "directory-preview");
            worker.setDaemon(true);
            worker.start();
        };
        debounce.setOnFinished(e -> update.run());
        includes.textProperty().addListener((obs, o, n) -> debounce.playFromStart());
        excludes.textProperty().addListener((obs, o, n) -> debounce.playFromStart());
        recursive.selectedProperty().addListener((obs, o, n) -> debounce.playFromStart());
        update.run();

        Optional<ButtonType> answer = dialog.showAndWait();
        // a preview still running is ignored
        generation.incrementAndGet();
        if (answer.isEmpty() || answer.get() != ok) return Optional.empty();
        return Optional.of(settings(includes, excludes, recursive));
    }

    private static DirectorySettings settings(TextArea includes, TextArea excludes, CheckBox recursive) {
        return new DirectorySettings(lines(includes.getText()), lines(excludes.getText()), recursive.isSelected());
    }

    private static List<String> lines(String text) {
        return Arrays.stream(text.split("\\R")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
