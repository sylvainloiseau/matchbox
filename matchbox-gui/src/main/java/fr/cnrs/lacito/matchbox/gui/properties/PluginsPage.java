package fr.cnrs.lacito.matchbox.gui.properties;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.gui.DisplayStyleDialog;
import fr.cnrs.lacito.matchbox.gui.view.DisplayStyleCss;
import fr.cnrs.lacito.matchbox.gui.view.HighlightStyles;
import fr.cnrs.lacito.matchbox.gui.view.KindCatalog;
import fr.cnrs.lacito.matchbox.model.project.DisplayStyles;
import fr.cnrs.lacito.matchbox.model.project.ProjectEdit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The installed plugins (those used by a file type first) with a sample of their display and
 * Display… (the display styles, shared by all the file types), then the plugins that are not
 * installed but have styles in the project, with Forget.
 */
final class PluginsPage implements PropertiesPage {

    /** A row; {@code plugin} is null if it is not installed. */
    record PluginRow(String grammarId, GrammarPlugin plugin) {
        @Override
        public String toString() {
            return plugin == null ? grammarId : plugin.getDisplayName();
        }
    }

    private final PageContext ctx;
    private final ObservableList<PluginRow> rows = FXCollections.observableArrayList();
    private final ListView<PluginRow> list = new ListView<>(rows);
    private final VBox content;

    PluginsPage(PageContext ctx) {
        this.ctx = ctx;
        list.setId("pluginParameters");
        list.setPrefHeight(270);
        list.setCellFactory(lv -> new PluginCell());
        VBox.setVgrow(list, Priority.ALWAYS);
        content = DialogParts.page(id(), list, DialogParts.help(
            "The display of a plugin is the same in every file type using it. "
                + "Check a plugin in a file type to edit its display."));
        refresh();
    }

    @Override
    public ProjectPropertiesPage id() {
        return ProjectPropertiesPage.PLUGINS;
    }

    @Override
    public Node content() {
        return content;
    }

    @Override
    public void shown() {
        refresh();
    }

    /** Selects the row of a plugin and scrolls to it. */
    void reveal(String grammarId) {
        refresh();
        rows.stream().filter(r -> r.grammarId().equals(grammarId)).findFirst().ifPresent(r -> {
            list.getSelectionModel().select(r);
            list.scrollTo(r);
            list.requestFocus();
        });
    }

    /**
     * Installed plugins, those used by a file type first (order of first use), then the others by
     * name; then the plugins that are not installed but have display styles in the project.
     */
    private void refresh() {
        ProjectEdit edit = ctx.edit();
        List<GrammarPlugin> installed = PluginManager.getAvailableGrammars();
        List<String> used = edit.usedGrammarIds();
        List<PluginRow> next = new ArrayList<>();
        for (String id : used) {
            installed.stream().filter(g -> g.getId().equals(id)).findFirst()
                .ifPresent(g -> next.add(new PluginRow(id, g)));
        }
        installed.stream().filter(g -> !used.contains(g.getId()))
            .sorted(Comparator.comparing(GrammarPlugin::getDisplayName, String.CASE_INSENSITIVE_ORDER))
            .forEach(g -> next.add(new PluginRow(g.getId(), g)));
        edit.getDisplayStyles().asMap().keySet().stream().map(DisplayStyles.Key::grammarId).distinct()
            .filter(id -> installed.stream().noneMatch(g -> g.getId().equals(id)))
            .sorted()
            .forEach(id -> next.add(new PluginRow(id, null)));
        PluginRow selected = list.getSelectionModel().getSelectedItem();
        rows.setAll(next);
        if (selected != null) {
            next.stream().filter(r -> r.grammarId().equals(selected.grammarId())).findFirst()
                .ifPresent(r -> list.getSelectionModel().select(r));
        }
        list.refresh();
    }

    /**
     * Name and description (shortened with an ellipsis), a sample of the plugin's display, the file
     * types using it (or a link to the File types tab) and, right aligned, Display… (enabled when the
     * plugin is used) or Forget (a plugin that is not installed).
     */
    private class PluginCell extends ListCell<PluginRow> {
        private final Label name = new Label();
        private final Label description = new Label();
        private final Label swatch = new Label("Aa");
        private final Label usedIn = new Label();
        private final Hyperlink notUsed = new Hyperlink("not used");
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
            usedIn.managedProperty().bind(usedIn.visibleProperty());
            notUsed.setId("pluginNotUsed");
            notUsed.setMinWidth(0);
            notUsed.setPrefWidth(150);
            notUsed.setMaxWidth(150);
            notUsed.managedProperty().bind(notUsed.visibleProperty());
            notUsed.setTooltip(new Tooltip("Checked in no file type: check it in the File types tab"));
            notUsed.setOnAction(e -> ctx.select(ProjectPropertiesPage.FILE_TYPES));
            button.setMinWidth(Region.USE_PREF_SIZE);
            box = new HBox(10, name, description, swatch, usedIn, notUsed, button);
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
            ProjectEdit edit = ctx.edit();
            DisplayStyle style = edit.getDisplayStyles().get(DisplayStyles.Key.grammar(row.grammarId()));
            if (row.plugin() == null) {
                name.setText(row.grammarId());
                description.setText("(not installed)");
                swatch.setStyle("-fx-padding: 0 4 0 4;" + DisplayStyleCss.label(style));
                usedIn.setVisible(true);
                usedIn.setText("");
                usedIn.setTooltip(null);
                notUsed.setVisible(false);
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
                usedIn.setVisible(!types.isEmpty());
                notUsed.setVisible(types.isEmpty());
                usedIn.setText(String.join(", ", types));
                usedIn.setTooltip(types.isEmpty() ? null : new Tooltip("Checked in: " + String.join(", ", types)));
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
        DisplayStyles styles = ctx.edit().getDisplayStyles();
        for (DisplayStyles.Key key : List.copyOf(styles.asMap().keySet())) {
            if (key.grammarId().equals(grammarId)) styles = styles.with(key, DisplayStyle.EMPTY);
        }
        ctx.edit().setDisplayStyles(styles);
        refresh();
        ctx.changed();
    }

    /** Edits the display styles of a grammar in the working copy (applied by Save). */
    private void editDisplay(GrammarPlugin g) {
        List<String> declared;
        try {
            declared = g.getObjectKinds();
        } catch (RuntimeException | LinkageError e) {
            declared = List.of();
        }
        List<String> kinds = KindCatalog.kinds(g.getId(), declared, ctx.project().getCorpus(),
            ctx.edit().getDisplayStyles());
        new DisplayStyleDialog(g, kinds, ctx.edit().getDisplayStyles()).showAndWait(ctx.stage())
            .ifPresent(styles -> {
                ctx.edit().setDisplayStyles(styles);
                refresh();
                ctx.changed();
            });
    }
}
