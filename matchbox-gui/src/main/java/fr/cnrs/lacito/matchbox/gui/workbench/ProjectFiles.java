package fr.cnrs.lacito.matchbox.gui.workbench;

import fr.cnrs.lacito.matchbox.gui.DirectorySettingsDialog;
import fr.cnrs.lacito.matchbox.gui.ErrorReporter;
import fr.cnrs.lacito.matchbox.gui.UnsavedChangesDialog;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.model.project.DirectorySettings;
import fr.cnrs.lacito.matchbox.model.project.Project;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.DirectoryChooser;

/**
 * The files and directories of the project, as the user changes them from the window's menus and
 * from File Mode's tree: adding a directory with its settings, editing them, rescanning, excluding
 * a file or a sub-directory, removing a directory or a file added alone. A file of a directory
 * cannot be removed alone (it would come back when the project is opened again): it is excluded
 * in the directory's settings. The dialogs are replaceable, for the GUI tests.
 */
public final class ProjectFiles {

    /** Edits the settings of a directory; empty to cancel. */
    @FunctionalInterface
    public interface SettingsEditor {
        Optional<DirectorySettings> edit(Path directory, DirectorySettings initial, List<Path> current, boolean adding);
    }

    /** Asks to confirm a change; false to cancel. */
    @FunctionalInterface
    public interface Confirmer {
        boolean confirm(String title, String header, String content, String action);
    }

    /** Unsaved documents about to leave the project: false to cancel. */
    @FunctionalInterface
    public interface UnsavedAsker {
        boolean confirm(List<TextDocument> documents, String action);
    }

    private final Workbench workbench;
    private SettingsEditor settingsEditor;
    private Confirmer confirmer;
    private UnsavedAsker unsaved;
    private Supplier<File> directoryChooser;

    ProjectFiles(Workbench workbench) {
        this.workbench = workbench;
        this.settingsEditor = (directory, initial, current, adding) ->
            DirectorySettingsDialog.show(workbench.window(), directory, initial, current, adding);
        this.confirmer = (title, header, content, action) -> {
            ButtonType yes = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, content, yes, ButtonType.CANCEL);
            if (workbench.window() != null) alert.initOwner(workbench.window());
            alert.setTitle(title);
            alert.setHeaderText(header);
            return alert.showAndWait().orElse(ButtonType.CANCEL) == yes;
        };
        this.unsaved = (documents, action) -> UnsavedChangesDialog.confirm(workbench.window(), documents, action);
        this.directoryChooser = () -> new DirectoryChooser().showDialog(workbench.window());
    }

    /** Replaces the dialogs (GUI tests). */
    public void setDialogs(SettingsEditor settingsEditor, Confirmer confirmer, UnsavedAsker unsaved,
                           Supplier<File> directoryChooser) {
        this.settingsEditor = settingsEditor;
        this.confirmer = confirmer;
        this.unsaved = unsaved;
        this.directoryChooser = directoryChooser;
    }

    private Optional<Project> project() {
        return Optional.ofNullable(workbench.session().project());
    }

    /** True if the document is a file added alone (it can be removed from the project). */
    public boolean isRemovable(TextDocument document) {
        return project().map(p -> p.directoryOf(document.getFilePath()).isEmpty()).orElse(false);
    }

    /** Chooses a directory, then its settings (defaults from the settings), and adds it. */
    public void addDirectory() {
        Project project = project().orElse(null);
        if (project == null) return;
        File selected = directoryChooser.get();
        if (selected == null) return;
        Path directory = selected.toPath().toAbsolutePath().normalize();
        if (project.getDefinitionDirectories().contains(directory)) {
            workbench.status().warn("The directory " + directory + " is already in the project");
            return;
        }
        DirectorySettings defaults = DirectorySettings.of(workbench.settings().fileExtensions(), true);
        Optional<DirectorySettings> settings = settingsEditor.edit(directory, defaults, List.of(), true);
        if (settings.isEmpty()) return;
        try {
            project.addDirectory(directory, settings.get());
            workbench.settings().recentFiles().add(directory);
        } catch (Exception e) {
            ErrorReporter.report(workbench.window(), "Error adding the directory", e);
        }
    }

    /** Edits the settings of a directory of the project, then scans it again. */
    public void editSettings(Path directory) {
        Project project = project().orElse(null);
        if (project == null) return;
        DirectorySettings initial = project.directorySettings(directory).orElse(null);
        if (initial == null) return;
        settingsEditor.edit(directory, initial, project.filesOf(directory), false)
            .ifPresent(settings -> apply(project, directory, settings, "applying the directory settings"));
    }

    /** Scans a directory again (files created or deleted on the disk). */
    public void rescan(Path directory) {
        project().ifPresent(p -> p.directorySettings(directory).ifPresent(s ->
            apply(p, directory, s, "scanning the directory again")));
    }

    /** Adds an exclude pattern to the settings of a directory (a file or a sub-directory leaves the project). */
    public void exclude(Path directory, String pattern) {
        project().ifPresent(p -> p.directorySettings(directory).ifPresent(s ->
            apply(p, directory, s.withExclude(pattern), "excluding it from the project")));
    }

    /** Excludes a file of a directory ({@code exclude} of its relative path, anchored). */
    public void excludeFile(TextDocument document) {
        project().flatMap(p -> p.directoryOf(document.getFilePath())).ifPresent(directory ->
            exclude(directory, "/" + DirectorySettings.relativePath(directory, document.getFilePath())));
    }

    /** Excludes a sub-directory of a directory of the project. */
    public void excludeSubdirectory(Path directory, Path subdirectory) {
        exclude(directory, "/" + DirectorySettings.relativePath(directory, subdirectory) + "/");
    }

    private void apply(Project project, Path directory, DirectorySettings settings, String action) {
        try {
            List<TextDocument> removed = project.documentsRemovedBy(directory, settings);
            if (!unsaved.confirm(removed, action)) return;
            Project.DirectoryChange change = project.setDirectorySettings(directory, settings);
            workbench.status().info(directory.getFileName() + ": " + change.added().size() + " added, "
                + change.removed().size() + " removed");
        } catch (Exception e) {
            ErrorReporter.report(workbench.window(), "Error scanning the directory", e);
        }
    }

    /** Removes a directory and its files from the project, after confirmation (the files stay on the disk). */
    public void removeDirectory(Path directory) {
        Project project = project().orElse(null);
        if (project == null || !project.getDefinitionDirectories().contains(directory)) return;
        int files = project.filesOf(directory).size();
        if (!confirmer.confirm("Remove directory from project", "Remove " + directory + " from the project?",
                "Removes the directory and its " + files + (files == 1 ? " file" : " files")
                    + " from the project. The files are not deleted from the disk.", "Remove")) {
            return;
        }
        List<TextDocument> documents = project.getDerivedFileList().stream()
            .filter(d -> project.filesOf(directory).contains(d.getFilePath())).toList();
        if (unsaved.confirm(documents, "removing the directory from the project")) {
            project.removeDirectory(directory);
        }
    }

    /** Removes a file added alone, after asking about its unsaved changes; a file of a directory is refused. */
    public void removeFile(TextDocument document) {
        Project project = project().orElse(null);
        if (project == null) return;
        Optional<Path> directory = project.directoryOf(document.getFilePath());
        if (directory.isPresent()) {
            workbench.status().warn("This file comes from the directory " + directory.get()
                + ": exclude it in the directory settings");
            return;
        }
        if (unsaved.confirm(List.of(document), "removing the file from the project")) {
            project.removeFile(document.getFilePath());
        }
    }
}
