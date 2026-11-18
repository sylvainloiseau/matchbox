package fr.cnrs.lacito.matchbox.gui.properties;

import fr.cnrs.lacito.matchbox.gui.ErrorReporter;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.model.project.ProjectEdit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Edits the definition of a project, in tabs ({@link ProjectPropertiesPage}): General (name,
 * description), File types (regular expressions on file names and the grammars applied, in rank
 * order), Plugins (display styles, shared by all the file types), Corpus (inherited context, file
 * order, gloss check), AI assistant. Files and directories are added with the Current project menu
 * and listed in File Mode.
 *
 * <p>Every tab edits one {@link ProjectEdit} working copy: the project is changed only by Save
 * (documents whose grammars changed are then parsed again); Cancel discards everything, whatever the
 * tabs visited. A tab title shows "•" when its settings differ from the project and "⚠" when they
 * have problems; Save with problems shows the first one instead of saving.
 */
public class ProjectPropertiesDialog {

    private final Stage parentStage;
    private final Project project;
    private final ProjectEdit edit;
    private final ProjectPropertiesPage initial;
    private final Map<ProjectPropertiesPage, PropertiesPage> pages = new EnumMap<>(ProjectPropertiesPage.class);
    private final Map<ProjectPropertiesPage, Tab> tabs = new EnumMap<>(ProjectPropertiesPage.class);
    private final Label problemLabel = DialogParts.errorLabel();
    private Stage stage;
    private TabPane tabPane;
    private boolean saved = false;

    /** @param initial the tab shown first (null: General) */
    public ProjectPropertiesDialog(Stage parentStage, Project project, ProjectPropertiesPage initial) {
        this.parentStage = parentStage;
        this.project = project;
        this.edit = ProjectEdit.of(project);
        this.initial = initial == null ? ProjectPropertiesPage.GENERAL : initial;
    }

    public ProjectPropertiesDialog(Stage parentStage, Project project) {
        this(parentStage, project, null);
    }

    public void showAndWait() {
        build(true).showAndWait();
    }

    /** Shows the dialog without waiting and without blocking its owner (for the GUI tests). */
    public Stage show() {
        Stage s = build(false);
        s.show();
        return s;
    }

    public boolean isSaved() {
        return saved;
    }

    /** The tab selected (when the dialog was closed): where to open it next time. */
    public ProjectPropertiesPage selectedPage() {
        if (tabPane == null) return initial;
        return (ProjectPropertiesPage) tabPane.getSelectionModel().getSelectedItem().getUserData();
    }

    /** Selects a tab (for links and tests). */
    public void select(ProjectPropertiesPage page) {
        tabPane.getSelectionModel().select(tabs.get(page));
    }

    private Stage build(boolean modal) {
        stage = new Stage();
        if (modal) stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(parentStage);
        stage.setTitle("Project properties");
        stage.setWidth(820);
        stage.setHeight(600);
        stage.setMinWidth(640);
        stage.setMinHeight(480);

        PageContext ctx = new PageContext() {
            @Override public Project project() { return project; }
            @Override public ProjectEdit edit() { return edit; }
            @Override public Stage stage() { return stage; }
            @Override public void changed() { updateMarkers(); }
            @Override public void select(ProjectPropertiesPage page) { ProjectPropertiesDialog.this.select(page); }
            @Override public void showPlugin(String grammarId) {
                ProjectPropertiesDialog.this.select(ProjectPropertiesPage.PLUGINS);
                ((PluginsPage) pages.get(ProjectPropertiesPage.PLUGINS)).reveal(grammarId);
            }
        };
        for (PropertiesPage p : List.of(new GeneralPage(ctx), new FileTypesPage(ctx), new PluginsPage(ctx),
                new CorpusPage(ctx), new AssistantPage(ctx))) {
            pages.put(p.id(), p);
        }

        tabPane = new TabPane();
        tabPane.setId("projectPropertiesTabs");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        for (ProjectPropertiesPage id : ProjectPropertiesPage.values()) {
            Tab tab = new Tab(id.title(), pages.get(id).content());
            tab.setId(id.nodeId());
            tab.setUserData(id);
            tab.setClosable(false);
            tab.setTooltip(new Tooltip(id.heading()));
            tabs.put(id, tab);
            tabPane.getTabs().add(tab);
        }
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) pages.get((ProjectPropertiesPage) n.getUserData()).shown();
        });

        BorderPane root = new BorderPane(tabPane);
        root.setBottom(buttons());
        stage.setScene(new Scene(root));
        tabPane.getSelectionModel().select(tabs.get(initial));
        pages.get(initial).shown();
        updateMarkers();
        return stage;
    }

    private HBox buttons() {
        Button save = new Button("Save");
        save.setId("projectPropertiesSave");
        save.setPrefWidth(80);
        save.setDefaultButton(false);
        save.setOnAction(e -> handleSave());
        Button cancel = new Button("Cancel");
        cancel.setId("projectPropertiesCancel");
        cancel.setPrefWidth(80);
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> stage.close());
        problemLabel.setId("projectPropertiesProblem");
        problemLabel.setMinWidth(0);
        problemLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(problemLabel, Priority.ALWAYS);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = new HBox(10, problemLabel, spacer, save, cancel);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setPadding(new Insets(10, 12, 12, 12));
        return box;
    }

    /** "•" on the tabs whose settings changed, "⚠" on those with problems; the first problem under the tabs. */
    private void updateMarkers() {
        if (tabPane == null) return;
        Set<ProjectEdit.Topic> changed = edit.changedTopics(project);
        List<ProjectEdit.Problem> problems = edit.problems();
        for (ProjectPropertiesPage id : ProjectPropertiesPage.values()) {
            Tab tab = tabs.get(id);
            boolean isChanged = id.topics().stream().anyMatch(changed::contains);
            List<String> own = problems.stream().filter(p -> id.topics().contains(p.topic()))
                .map(ProjectEdit.Problem::message).toList();
            tab.setText(id.title() + (isChanged ? " •" : "") + (own.isEmpty() ? "" : " ⚠"));
            String tip = id.heading()
                + (isChanged ? "\nChanged: applied by Save" : "")
                + (own.isEmpty() ? "" : "\n" + own.stream().collect(Collectors.joining("\n")));
            tab.setTooltip(new Tooltip(tip));
        }
        if (problems.isEmpty()) {
            problemLabel.setVisible(false);
        } else {
            ProjectEdit.Problem first = problems.get(0);
            problemLabel.setText("⚠ " + first.message() + (problems.size() > 1
                ? " (and " + (problems.size() - 1) + " more)" : "") + " — "
                + ProjectPropertiesPage.of(first.topic()).title() + " tab");
            problemLabel.setVisible(true);
        }
    }

    /** Saves, or shows the field of the first problem (its tab, its file type) and saves nothing. */
    private void handleSave() {
        List<ProjectEdit.Problem> problems = edit.problems();
        if (!problems.isEmpty()) {
            ProjectEdit.Problem first = problems.get(0);
            ProjectPropertiesPage page = ProjectPropertiesPage.of(first.topic());
            select(page);
            pages.get(page).reveal(first);
            updateMarkers();
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
