package fr.cnrs.lacito.matchbox.gui.start;

import fr.cnrs.lacito.matchbox.settings.UserSettings;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javafx.beans.Observable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * What the main area shows while no project is open: New project, Open project, the recent
 * projects (a link opens one, × removes it from the list), and whether the last project is reopened
 * at startup. A {@code .mbx} file dropped on the window also opens it (see {@code MainController}).
 */
public final class StartPage extends StackPane {

    private final UserSettings settings;
    private final Consumer<Path> onOpenRecent;
    private final VBox recentBox = new VBox(2);
    private final Button newProject = new Button("New project");

    public StartPage(UserSettings settings, Runnable onNewProject, Runnable onOpenProject, Consumer<Path> onOpenRecent) {
        this.settings = settings;
        this.onOpenRecent = onOpenRecent;
        setId("startPage");
        setStyle("-fx-background-color: #f4f4f4;");

        Label title = new Label("Matchbox");
        title.setStyle("-fx-font-size: 28; -fx-font-weight: bold;");
        Label subtitle = new Label("Create a project, or open one, to start.");
        subtitle.setStyle("-fx-text-fill: #555555;");

        newProject.setId("startNewProject");
        newProject.setTooltip(new Tooltip("A new project with the default file types and plugins"));
        newProject.setOnAction(e -> onNewProject.run());
        Button openProject = new Button("Open project…");
        openProject.setId("startOpenProject");
        openProject.setTooltip(new Tooltip("Open a Matchbox project file (.mbx)"));
        openProject.setOnAction(e -> onOpenProject.run());
        HBox buttons = new HBox(10, newProject, openProject);
        buttons.setAlignment(Pos.CENTER);

        Label recentTitle = new Label("Recent projects");
        recentTitle.setStyle("-fx-font-weight: bold;");
        recentBox.setId("startRecentProjects");

        CheckBox reopen = new CheckBox("Reopen the last project at startup");
        reopen.selectedProperty().bindBidirectional(settings.openLastProjectProperty());

        VBox content = new VBox(14, title, subtitle, buttons, new VBox(4, recentTitle, recentBox), reopen);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(30));
        content.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        content.setMinWidth(420);
        content.setStyle("-fx-background-color: white; -fx-background-radius: 6; -fx-border-color: #dddddd; -fx-border-radius: 6;");
        getChildren().add(content);

        refreshRecent();
        settings.recentProjects().items().addListener((Observable o) -> refreshRecent());
        // the first control to take the keyboard when the page appears
        visibleProperty().addListener((obs, o, visible) -> {
            if (visible) newProject.requestFocus();
        });
    }

    private void refreshRecent() {
        recentBox.getChildren().clear();
        List<Path> recent = settings.recentProjects().existing();
        if (recent.isEmpty()) {
            Label none = new Label("No recent projects");
            none.setStyle("-fx-text-fill: #777777;");
            recentBox.getChildren().add(none);
            return;
        }
        for (Path path : recent) {
            Path name = path.getFileName();
            Hyperlink link = new Hyperlink(name == null ? path.toString() : name.toString());
            link.setTooltip(new Tooltip("Open " + path));
            link.setOnAction(e -> {
                link.setVisited(false);
                onOpenRecent.accept(path);
            });
            Path parent = path.getParent();
            Label where = new Label(parent == null ? "" : abbreviate(parent));
            where.setStyle("-fx-text-fill: #888888; -fx-font-size: 0.9em;");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button remove = new Button("×");
            remove.setStyle("-fx-font-size: 0.85em; -fx-padding: 0 5 0 5;");
            remove.setTooltip(new Tooltip("Remove from recent projects (the file is kept)"));
            remove.setAccessibleText("Remove " + path + " from recent projects");
            remove.setOnAction(e -> settings.recentProjects().remove(path));
            HBox row = new HBox(6, link, where, spacer, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            recentBox.getChildren().add(row);
        }
    }

    /** {@code ~/notes} for a directory of the user's home. */
    private static String abbreviate(Path dir) {
        String home = System.getProperty("user.home");
        String s = dir.toString();
        return home != null && !home.isEmpty() && s.startsWith(home) ? "~" + s.substring(home.length()) : s;
    }
}
