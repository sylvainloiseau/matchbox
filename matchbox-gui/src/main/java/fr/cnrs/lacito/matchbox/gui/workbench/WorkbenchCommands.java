package fr.cnrs.lacito.matchbox.gui.workbench;

import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.gui.ErrorReporter;
import fr.cnrs.lacito.matchbox.gui.UnsavedChangesDialog;
import fr.cnrs.lacito.matchbox.gui.mode.ModeHost;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.SegmentAtCaret;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.model.project.DirectorySettings;
import fr.cnrs.lacito.matchbox.model.project.Project;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuItem;

/**
 * The commands of the window's menus. They mean the same in every mode: they act on the project,
 * on the undo journal, or on the focus of the active mode (the "focused text"). The functions of a
 * mode are in the mode's own menu.
 */
public final class WorkbenchCommands {

    private final Workbench workbench;
    /** True while the focused text has unsaved changes. */
    private final BooleanProperty focusedDirty = new SimpleBooleanProperty(false);
    /** True while a file added alone is focused (a file of a directory cannot be removed alone). */
    private final BooleanProperty removableFocused = new SimpleBooleanProperty(false);
    /** True while the project has directories. */
    private final BooleanProperty projectHasDirectories = new SimpleBooleanProperty(false);
    private final javafx.beans.InvalidationListener definitionListener = o -> {
        projectHasDirectories.set(this.boundProject != null && !this.boundProject.getDefinitionDirectories().isEmpty());
        bindFocus();
    };
    private Project boundProject;
    /** True while the project has files. */
    private final BooleanProperty projectHasFiles = new SimpleBooleanProperty(false);
    /** True while a document of the project has unsaved changes. */
    private final BooleanProperty anyDirty = new SimpleBooleanProperty(false);

    public WorkbenchCommands(Workbench workbench) {
        this.workbench = workbench;
        workbench.session().projectProperty().addListener((obs, o, n) -> bindProject(n));
        bindProject(workbench.session().project());
    }

    /** Follows the focus of the active mode (call once the modes exist). */
    public void attach(ModeHost host) {
        host.focusProperty().addListener((obs, o, n) -> bindFocus());
        bindFocus();
    }

    private void bindProject(Project project) {
        projectHasFiles.unbind();
        anyDirty.unbind();
        if (boundProject != null) boundProject.definitionRevisionProperty().removeListener(definitionListener);
        boundProject = project;
        if (project == null) {
            projectHasFiles.set(false);
            anyDirty.set(false);
            projectHasDirectories.set(false);
        } else {
            projectHasFiles.bind(Bindings.isNotEmpty(project.getDerivedFileList()));
            anyDirty.bind(project.anyDocumentDirtyProperty());
            project.definitionRevisionProperty().addListener(definitionListener);
            projectHasDirectories.set(!project.getDefinitionDirectories().isEmpty());
        }
    }

    private void bindFocus() {
        focusedDirty.unbind();
        Optional<TextDocument> document = focusedDocument();
        removableFocused.set(document.map(workbench.files()::isRemovable).orElse(false));
        if (document.isPresent()) {
            focusedDirty.bind(document.get().getCouldBeWrittenBooleanProperty());
        } else {
            focusedDirty.set(false);
        }
    }

    /** Binds the enabled state and the labels of the window's menu items. */
    public void bindMenus(MenuItem saveCurrentFile, MenuItem saveAll, MenuItem removeFile, MenuItem removeDirectory,
                          MenuItem removeAllFiles, MenuItem undo, MenuItem redo, MenuItem back, MenuItem forward) {
        saveCurrentFile.disableProperty().bind(focusedDirty.not());
        saveAll.disableProperty().bind(anyDirty.not());
        removeFile.disableProperty().bind(removableFocused.not());
        removeDirectory.disableProperty().bind(projectHasDirectories.not());
        removeAllFiles.disableProperty().bind(projectHasFiles.not());
        var journal = workbench.journal();
        undo.disableProperty().bind(journal.undoAvailableProperty().not());
        undo.textProperty().bind(Bindings.when(journal.undoAvailableProperty())
            .then(Bindings.concat("Undo ", journal.undoLabelProperty())).otherwise("Undo"));
        redo.disableProperty().bind(journal.redoAvailableProperty().not());
        redo.textProperty().bind(Bindings.when(journal.redoAvailableProperty())
            .then(Bindings.concat("Redo ", journal.redoLabelProperty())).otherwise("Redo"));
        var history = workbench.navigator().history();
        back.disableProperty().bind(history.canGoBackProperty().not());
        forward.disableProperty().bind(history.canGoForwardProperty().not());
    }

    /** The document of the active mode's focus. */
    public Optional<TextDocument> focusedDocument() {
        Project project = workbench.session().project();
        if (project == null) return Optional.empty();
        return workbench.focus().flatMap(f -> project.document(f.text()));
    }

    // ------------------------------------------------------------------
    // Files
    // ------------------------------------------------------------------

    /** Adds a file or the files of a directory to the project, and remembers it as recent. */
    public void addFileOrDirectory(File f) {
        Project project = workbench.session().project();
        if (f == null || project == null) return;
        try {
            if (f.isDirectory()) {
                project.addDirectory(f.toPath(),
                    DirectorySettings.of(workbench.settings().fileExtensions(), true));
            } else {
                project.addFile(f.toPath());
            }
            workbench.settings().recentFiles().add(f.toPath());
        } catch (Exception e) {
            ErrorReporter.report(workbench.window(), "Error opening file", e);
        }
    }

    /** Chooses a directory, then its settings, and adds it to the project. */
    public void addDirectory() {
        workbench.files().addDirectory();
    }

    public void saveCurrentFile() {
        Optional<TextDocument> document = focusedDocument();
        if (document.isEmpty() || !document.get().getCouldBeWritten()) return;
        try {
            document.get().writeFile();
        } catch (IOException e) {
            ErrorReporter.report(workbench.window(), "Error saving file", e);
        }
    }

    public void saveAll() {
        workbench.saveAll();
    }

    /** Removes the focused file from the project if it was added alone (asks about unsaved changes). */
    public void removeFile() {
        focusedDocument().ifPresent(workbench.files()::removeFile);
    }

    /**
     * Removes every file and directory from the project, after confirmation (the files stay on
     * the disk), then for unsaved files.
     */
    public void removeAllFiles() {
        Project project = workbench.session().project();
        if (project == null) return;
        int files = project.getDerivedFileList().size();
        long directories = project.getDefinitionPaths().stream().filter(project::isDefinitionDirectory).count();
        ButtonType remove = new ButtonType("Remove", ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", remove, ButtonType.CANCEL);
        if (workbench.window() != null) alert.initOwner(workbench.window());
        alert.setTitle("Remove all files from project");
        alert.setHeaderText("Remove all the files from the project?");
        alert.setContentText("Removes " + files + (files == 1 ? " file" : " files")
            + (directories == 0 ? "" : " (" + directories + (directories == 1 ? " directory" : " directories") + ")")
            + " from the project. The files are not deleted from the disk.");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != remove) {
            return;
        }
        if (!UnsavedChangesDialog.confirm(workbench.window(), List.copyOf(project.getDerivedFileList()),
                "removing all the files from the project")) {
            return;
        }
        for (Path p : project.getDefinitionPaths()) {
            if (project.isDefinitionDirectory(p)) {
                project.removeDirectory(p);
            } else {
                project.removeFile(p);
            }
        }
        for (TextDocument d : List.copyOf(project.getDerivedFileList())) {
            project.removeFile(d.getFilePath());
        }
    }

    // ------------------------------------------------------------------
    // Edit and View
    // ------------------------------------------------------------------

    public void undo() {
        workbench.undo();
    }

    public void redo() {
        workbench.redo();
    }

    /** Plays (or pauses) the focused media segment, or the segment at the focus. */
    public void playSegment() {
        Optional<Focus> focus = workbench.focus();
        if (focus.isEmpty()) {
            workbench.status().info("No media segment here");
            return;
        }
        Optional<MediaSegment> segment = focus.get().object()
            .filter(MediaSegment.class::isInstance).map(MediaSegment.class::cast)
            .or(() -> workbench.session().corpus().text(focus.get().text())
                .flatMap(t -> SegmentAtCaret.find(t, focus.get().start())));
        if (segment.isEmpty()) {
            workbench.status().info("No media segment here");
            return;
        }
        workbench.playback().toggle(segment.get());
    }

    public void back() {
        workbench.navigator().back();
    }

    public void forward() {
        workbench.navigator().forward();
    }
}
