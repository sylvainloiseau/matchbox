package fr.cnrs.lacito.matchbox.gui.mode;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.corpus.CorpusView;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.Tag;
import fr.cnrs.lacito.matchbox.gui.ErrorReporter;
import fr.cnrs.lacito.matchbox.gui.edit.ViewEditService;
import fr.cnrs.lacito.matchbox.gui.mode.tagBrowser.OccurrenceCell;
import fr.cnrs.lacito.matchbox.gui.mode.tagBrowser.OccurrenceExport;
import fr.cnrs.lacito.matchbox.gui.mode.tagBrowser.OccurrenceRow;
import fr.cnrs.lacito.matchbox.gui.mode.tagBrowser.TagDialogs;
import fr.cnrs.lacito.matchbox.gui.mode.tagBrowser.TagIndex;
import fr.cnrs.lacito.matchbox.gui.mode.tagBrowser.TagNode;
import fr.cnrs.lacito.matchbox.gui.mode.tagBrowser.TagOccurrence;
import fr.cnrs.lacito.matchbox.gui.mode.tagBrowser.TagPathEdit;
import fr.cnrs.lacito.matchbox.gui.mode.tagBrowser.TagTree;
import fr.cnrs.lacito.matchbox.gui.mode.tagBrowser.TagTreeCell;
import fr.cnrs.lacito.matchbox.gui.workbench.Focus;
import fr.cnrs.lacito.matchbox.gui.workbench.Workbench;
import fr.cnrs.lacito.matchbox.settings.TagBrowserSettingsData;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * Tag Browser: every {@link Tag} of the corpus, whatever plugin and notation made it, as a tree of
 * the levels of the keywords with the number of tags of each node; the occurrences of the selected
 * node with a few lines of context; rename and move (drag and drop) of a node everywhere, as one
 * batch edit of the texts. See {@code .claude/plans/TAG_BROWSER_PLAN.md}.
 */
public final class TagBrowserMode extends AbstractMode {

    public static final String ID = "tag";

    private final SplitPane root = new SplitPane();
    private final TextField filterField = new TextField();
    private final ChoiceBox<TagTree.Sort> sortChoice = new ChoiceBox<>();
    private final TreeView<TagNode> tree = new TreeView<>();
    /** Shown over the tree when it is empty (no tags, or no match for the filter). */
    private final Label emptyTree = new Label();
    private final Label topLevelDrop = new Label("⤒  Drop here to move to the top level");
    private final Label title = new Label();
    private final Label counts = new Label();
    private final Spinner<Integer> beforeSpinner;
    private final Spinner<Integer> afterSpinner;
    private final CheckBox includeSubTags = new CheckBox("Include sub-tags");
    private final ChoiceBox<TypeChoice> appliesTo = new ChoiceBox<>();
    private final Label stickyHeader = new Label();
    private final ListView<OccurrenceRow> list = new ListView<>();
    private final Menu menu;

    private TagIndex index = new TagIndex();
    private TagNode fullRoot = TagTree.of(List.of(), TagTree.Sort.NAME);
    /** Path of the selected node; empty if none. */
    private List<String> selectedPath = List.of();
    /** Path to select once it exists (after a rename or a move). */
    private List<String> pendingPath;
    /** The occurrences shown (all types). */
    private List<TagOccurrence> shown = List.of();
    private TagNode dragged;
    /** True while the tree or the list is rebuilt (their selection events are not the user's). */
    private boolean rebuilding;

    /** A choice of the "Applies to" filter: a type (null for all) and its count. */
    private record TypeChoice(String type, int count) {
        @Override
        public String toString() {
            return (type == null ? "All types" : type) + " (" + count + ")";
        }
    }

    public TagBrowserMode(Workbench workbench) {
        super(workbench);
        TagBrowserSettingsData s = settings();
        beforeSpinner = new Spinner<>(0, TagBrowserSettingsData.MAX_CONTEXT, s.contextBefore());
        afterSpinner = new Spinner<>(0, TagBrowserSettingsData.MAX_CONTEXT, s.contextAfter());
        // ids for tests and accessibility tools
        tree.setId("tagTree");
        filterField.setId("tagFilter");
        list.setId("tagOccurrences");
        counts.setId("tagCounts");
        beforeSpinner.setId("contextBefore");
        afterSpinner.setId("contextAfter");
        appliesTo.setId("appliesTo");
        includeSubTags.setId("includeSubTags");
        root.getItems().addAll(buildTreePane(), buildOccurrencePane());
        root.setDividerPositions(0.25);
        menu = buildMenu();
    }

    private TagBrowserSettingsData settings() {
        TagBrowserSettingsData s = workbench.settings().tagBrowserProperty().get();
        return s == null ? TagBrowserSettingsData.defaults() : s;
    }

    private void saveSettings(TagBrowserSettingsData s) {
        workbench.settings().tagBrowserProperty().set(s);
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private Node buildTreePane() {
        filterField.setPromptText("Filter tags…");
        filterField.textProperty().addListener((obs, o, n) -> rebuildTree());
        filterField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) filterField.clear();
        });
        sortChoice.getItems().setAll(TagTree.Sort.values());
        sortChoice.setConverter(new StringConverter<>() {
            public String toString(TagTree.Sort sort) {
                return sort == TagTree.Sort.COUNT ? "Count" : "A–Z";
            }

            public TagTree.Sort fromString(String s) {
                return null;
            }
        });
        sortChoice.setValue(sortOf(settings()));
        sortChoice.valueProperty().addListener((obs, o, n) -> {
            saveSettings(settings().withSort(n == TagTree.Sort.COUNT ? TagBrowserSettingsData.SORT_COUNT : TagBrowserSettingsData.SORT_NAME));
            rebuildTree();
        });
        HBox sortBox = new HBox(6, new Label("Sort:"), sortChoice);
        sortBox.setAlignment(Pos.CENTER_LEFT);

        tree.setShowRoot(false);
        // the tree uses F2 itself (edit): rename and move are handled before it
        tree.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F2 && !e.isShortcutDown()) {
                selectedNode().ifPresent(this::rename);
                e.consume();
            } else if (e.getCode() == KeyCode.F6 && !e.isShortcutDown()) {
                selectedNode().ifPresent(this::moveTo);
                e.consume();
            }
        });
        tree.setCellFactory(t -> new TagTreeCell(treeActions));
        tree.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (rebuilding || n == null) return;
            selectedPath = n.getValue().path();
            showOccurrences();
        });
        emptyTree.setWrapText(true);
        emptyTree.setStyle("-fx-text-fill: #777777;");
        emptyTree.setMouseTransparent(true);
        // scroll while dragging near the top or bottom edge
        tree.addEventFilter(DragEvent.DRAG_OVER, e -> {
            if (dragged == null) return;
            verticalScrollBar(tree).ifPresent(bar -> {
                if (e.getY() < 24) bar.decrement();
                else if (e.getY() > tree.getHeight() - 24) bar.increment();
            });
        });
        StackPane treeBox = new StackPane(tree, emptyTree);
        VBox.setVgrow(treeBox, Priority.ALWAYS);

        topLevelDrop.setMaxWidth(Double.MAX_VALUE);
        topLevelDrop.setStyle("-fx-border-color: #888888; -fx-border-style: dashed; -fx-padding: 4;");
        topLevelDrop.setVisible(false);
        topLevelDrop.setManaged(false);
        topLevelDrop.setOnDragOver(e -> {
            if (dragged != null && refusal(dragged.path(), List.of()).isEmpty()) e.acceptTransferModes(TransferMode.MOVE);
            e.consume();
        });
        topLevelDrop.setOnDragDropped(e -> {
            boolean ok = dragged != null && refusal(dragged.path(), List.of()).isEmpty();
            e.setDropCompleted(ok);
            e.consume();
            if (ok) {
                TagNode node = dragged;
                Platform.runLater(() -> move(node, List.of()));
            }
        });

        VBox pane = new VBox(6, filterField, sortBox, topLevelDrop, treeBox);
        pane.setPadding(new Insets(6));
        return pane;
    }

    private Node buildOccurrencePane() {
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 1.1em;");
        counts.setStyle("-fx-text-fill: #555555;");
        HBox titleBox = new HBox(12, title, counts);
        titleBox.setAlignment(Pos.BASELINE_LEFT);
        titleBox.setPadding(new Insets(6, 6, 0, 6));

        beforeSpinner.setEditable(true);
        afterSpinner.setEditable(true);
        beforeSpinner.setPrefWidth(70);
        afterSpinner.setPrefWidth(70);
        beforeSpinner.setTooltip(new Tooltip("Lines shown before each tag"));
        afterSpinner.setTooltip(new Tooltip("Lines shown after each tag"));
        beforeSpinner.valueProperty().addListener((obs, o, n) -> contextChanged());
        afterSpinner.valueProperty().addListener((obs, o, n) -> contextChanged());
        includeSubTags.setSelected(Boolean.TRUE.equals(settings().includeSubTags()));
        includeSubTags.setTooltip(new Tooltip("List the tags of the whole subtree of the selected node"));
        includeSubTags.selectedProperty().addListener((obs, o, n) -> {
            saveSettings(settings().withIncludeSubTags(n));
            showOccurrences();
        });
        appliesTo.setTooltip(new Tooltip("Only the tags applying to this type of object"));
        appliesTo.valueProperty().addListener((obs, o, n) -> {
            if (!rebuilding) showRows();
        });
        ToolBar toolBar = new ToolBar(
            new Label("Before"), beforeSpinner, new Label("After"), afterSpinner,
            new javafx.scene.control.Separator(Orientation.VERTICAL), includeSubTags,
            new javafx.scene.control.Separator(Orientation.VERTICAL), new Label("Applies to:"), appliesTo);

        stickyHeader.setMaxWidth(Double.MAX_VALUE);
        stickyHeader.setStyle("-fx-background-color: #eeeeee; -fx-font-weight: bold; -fx-padding: 3 6 3 6;");
        stickyHeader.setVisible(false);
        stickyHeader.setManaged(false);

        list.setCellFactory(l -> new OccurrenceCell(() -> workbench.session().corpus(),
            beforeSpinner::getValue, afterSpinner::getValue, () -> workbench.settings().tagColorProperty().get(), this::edit));
        list.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n instanceof OccurrenceRow.Occurrence occ) {
                setFocus(Optional.of(Focus.of(occ.occurrence().tag())));
            } else if (!rebuilding) {
                setFocus(Optional.empty());
            }
        });
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && list.getSelectionModel().getSelectedItem() instanceof OccurrenceRow.Occurrence o) {
                edit(o.occurrence());
                e.consume();
            }
        });
        list.skinProperty().addListener((obs, o, n) -> Platform.runLater(() ->
            verticalScrollBar(list).ifPresent(bar -> bar.valueProperty().addListener((b, x, y) -> updateStickyHeader()))));
        VBox.setVgrow(list, Priority.ALWAYS);
        showPlaceholder();

        return new VBox(titleBox, toolBar, stickyHeader, list);
    }

    private static Optional<ScrollBar> verticalScrollBar(Node control) {
        return control.lookupAll(".scroll-bar").stream()
            .filter(n -> n instanceof ScrollBar b && b.getOrientation() == Orientation.VERTICAL)
            .map(ScrollBar.class::cast)
            .findFirst();
    }

    private String emptyTreeMessage() {
        boolean tagPlugin = PluginManager.getAvailableGrammars().stream().anyMatch(g -> g.getId().equals("tag-grammar"));
        return "No tags in the project." + (tagPlugin ? "\nWrite #\"keyword\" in a note to tag it." : "");
    }

    // ------------------------------------------------------------------
    // The mode's menu
    // ------------------------------------------------------------------

    private Menu buildMenu() {
        MenuItem rename = new MenuItem("Rename tag…");
        rename.setAccelerator(new KeyCodeCombination(KeyCode.F2));
        rename.setOnAction(e -> selectedNode().ifPresent(this::rename));
        MenuItem moveTo = new MenuItem("Move to…");
        moveTo.setAccelerator(new KeyCodeCombination(KeyCode.F6));
        moveTo.setOnAction(e -> selectedNode().ifPresent(this::moveTo));
        MenuItem expandAll = new MenuItem("Expand all");
        expandAll.setOnAction(e -> setExpanded(tree.getRoot(), true));
        MenuItem collapseAll = new MenuItem("Collapse all");
        collapseAll.setOnAction(e -> tree.getRoot().getChildren().forEach(c -> setExpanded(c, false)));
        ToggleGroup sortGroup = new ToggleGroup();
        RadioMenuItem byName = new RadioMenuItem("Sort by name");
        RadioMenuItem byCount = new RadioMenuItem("Sort by count");
        byName.setToggleGroup(sortGroup);
        byCount.setToggleGroup(sortGroup);
        byName.setOnAction(e -> sortChoice.setValue(TagTree.Sort.NAME));
        byCount.setOnAction(e -> sortChoice.setValue(TagTree.Sort.COUNT));
        CheckMenuItem sub = new CheckMenuItem("Include sub-tags");
        sub.selectedProperty().bindBidirectional(includeSubTags.selectedProperty());
        MenuItem edit = new MenuItem("Edit occurrence in File Mode (Enter)");
        edit.setOnAction(e -> {
            if (list.getSelectionModel().getSelectedItem() instanceof OccurrenceRow.Occurrence o) this.edit(o.occurrence());
        });
        MenuItem copyKeyword = new MenuItem("Copy keyword");
        copyKeyword.setOnAction(e -> selectedNode().ifPresent(this::copyKeyword));
        MenuItem copyOccurrences = new MenuItem("Copy occurrences");
        copyOccurrences.setOnAction(e -> copy(OccurrenceExport.text(listed(), corpus())));
        MenuItem export = new MenuItem("Export occurrences as CSV…");
        export.setOnAction(e -> exportCsv());

        Menu result = new Menu("Tag Browser", null, rename, moveTo, new SeparatorMenuItem(), expandAll, collapseAll,
            new SeparatorMenuItem(), byName, byCount, sub, new SeparatorMenuItem(), edit, copyKeyword, copyOccurrences, export);
        result.setOnShowing(e -> {
            boolean none = selectedNode().isEmpty();
            rename.setDisable(none);
            moveTo.setDisable(none);
            copyKeyword.setDisable(none);
            edit.setDisable(!(list.getSelectionModel().getSelectedItem() instanceof OccurrenceRow.Occurrence));
            copyOccurrences.setDisable(listed().isEmpty());
            export.setDisable(listed().isEmpty());
            (sortChoice.getValue() == TagTree.Sort.COUNT ? byCount : byName).setSelected(true);
        });
        return result;
    }

    @Override
    public Optional<Menu> menu() {
        return Optional.of(menu);
    }

    @Override
    public Node root() {
        return root;
    }

    // ------------------------------------------------------------------
    // Corpus changes
    // ------------------------------------------------------------------

    @Override
    protected void onProjectChanged() {
        index = TagIndex.of(corpus());
        selectedPath = List.of();
        rebuildTree();
        showOccurrences();
    }

    @Override
    protected void onTextsChanged(Set<TextId> texts) {
        for (TextId id : texts) {
            index.update(id, corpus().text(id));
        }
        rebuildTree();
        showOccurrences();
    }

    private CorpusView corpus() {
        return workbench.session().corpus();
    }

    private static TagTree.Sort sortOf(TagBrowserSettingsData s) {
        return TagBrowserSettingsData.SORT_COUNT.equals(s.sort()) ? TagTree.Sort.COUNT : TagTree.Sort.NAME;
    }

    /** Rebuilds the tree, keeping the expanded nodes and the selection (by path). */
    private void rebuildTree() {
        Set<List<String>> expanded = new HashSet<>();
        if (tree.getRoot() != null) collectExpanded(tree.getRoot(), expanded);
        fullRoot = TagTree.of(index.all(), sortChoice.getValue() == null ? TagTree.Sort.NAME : sortChoice.getValue());
        if (pendingPath != null && fullRoot.find(pendingPath).isPresent()) {
            selectedPath = pendingPath;
            pendingPath = null;
            // show the moved or renamed node
            for (int i = 1; i < selectedPath.size(); i++) expanded.add(selectedPath.subList(0, i));
        }
        boolean filtering = !filterField.getText().isBlank();
        TagNode visible = TagTree.filter(fullRoot, filterField.getText());
        rebuilding = true;
        try {
            TreeItem<TagNode> rootItem = item(visible, expanded, filtering);
            rootItem.setExpanded(true);
            tree.setRoot(rootItem);
            findItem(rootItem, selectedPath).ifPresentOrElse(item -> {
                tree.getSelectionModel().select(item);
                int row = tree.getRow(item);
                if (row >= 0) tree.scrollTo(Math.max(0, row - 3));
            }, () -> tree.getSelectionModel().clearSelection());
        } finally {
            rebuilding = false;
        }
        if (fullRoot.find(selectedPath).isEmpty()) {
            selectedPath = List.of();
        }
        String empty = fullRoot.children().isEmpty() ? emptyTreeMessage()
            : visible.children().isEmpty() ? "No tag matches “" + filterField.getText().strip() + "”" : "";
        emptyTree.setText(empty);
        emptyTree.setVisible(!empty.isEmpty());
    }

    private static TreeItem<TagNode> item(TagNode node, Set<List<String>> expanded, boolean expandAll) {
        TreeItem<TagNode> item = new TreeItem<>(node);
        List<TreeItem<TagNode>> children = new ArrayList<>();
        for (TagNode child : node.children()) {
            children.add(item(child, expanded, expandAll));
        }
        item.getChildren().setAll(children);
        item.setExpanded(expandAll || expanded.contains(node.path()));
        return item;
    }

    private static void collectExpanded(TreeItem<TagNode> item, Set<List<String>> into) {
        if (item.isExpanded() && item.getValue() != null) into.add(item.getValue().path());
        item.getChildren().forEach(c -> collectExpanded(c, into));
    }

    private static Optional<TreeItem<TagNode>> findItem(TreeItem<TagNode> item, List<String> path) {
        if (path.isEmpty()) return Optional.empty();
        if (item.getValue() != null && item.getValue().path().equals(path)) return Optional.of(item);
        for (TreeItem<TagNode> child : item.getChildren()) {
            List<String> p = child.getValue().path();
            if (path.size() >= p.size() && path.subList(0, p.size()).equals(p)) return findItem(child, path);
        }
        return Optional.empty();
    }

    private static void setExpanded(TreeItem<?> item, boolean expanded) {
        if (item == null) return;
        item.setExpanded(expanded);
        item.getChildren().forEach(c -> setExpanded(c, expanded));
    }

    private Optional<TagNode> selectedNode() {
        return fullRoot.find(selectedPath).filter(n -> !n.isRoot());
    }

    // ------------------------------------------------------------------
    // Occurrences
    // ------------------------------------------------------------------

    /** Lists the occurrences of the selected node, keeping the selected occurrence. */
    private void showOccurrences() {
        Optional<TagNode> node = selectedNode();
        if (node.isEmpty()) {
            shown = List.of();
            title.setText("");
            counts.setText("");
            appliesTo.getItems().clear();
            list.getItems().clear();
            showPlaceholder();
            return;
        }
        shown = index.at(selectedPath, includeSubTags.isSelected());
        title.setText(TagPathEdit.display(selectedPath));
        // the types of objects the tags apply to, with their counts
        Map<String, Integer> types = new LinkedHashMap<>();
        shown.forEach(o -> types.merge(o.targetType(), 1, Integer::sum));
        String previous = appliesTo.getValue() == null ? null : appliesTo.getValue().type();
        List<TypeChoice> choices = new ArrayList<>();
        choices.add(new TypeChoice(null, shown.size()));
        types.forEach((t, n) -> choices.add(new TypeChoice(t, n)));
        rebuilding = true;
        try {
            appliesTo.getItems().setAll(choices);
            appliesTo.setValue(choices.stream().filter(c -> Objects.equals(c.type(), previous)).findFirst().orElse(choices.get(0)));
        } finally {
            rebuilding = false;
        }
        if (shown.isEmpty()) {
            int sub = node.get().total() - node.get().own();
            Hyperlink include = new Hyperlink("Include sub-tags");
            include.setOnAction(e -> includeSubTags.setSelected(true));
            VBox placeholder = new VBox(4, new Label("No tag is exactly " + node.get().keyword() + " · " + sub + " in sub-tags"), include);
            placeholder.setAlignment(Pos.CENTER);
            list.setPlaceholder(placeholder);
        }
        showRows();
    }

    /** The occurrences of the selected node that pass the "Applies to" filter. */
    private List<TagOccurrence> listed() {
        TypeChoice choice = appliesTo.getValue();
        if (choice == null || choice.type() == null) return shown;
        return shown.stream().filter(o -> o.targetType().equals(choice.type())).toList();
    }

    private void showRows() {
        Optional<TagOccurrence> selected = list.getSelectionModel().getSelectedItem() instanceof OccurrenceRow.Occurrence o
            ? Optional.of(o.occurrence()) : Optional.empty();
        List<TagOccurrence> listed = listed();
        long files = shown.stream().map(TagOccurrence::text).distinct().count();
        counts.setText(shown.size() + (shown.size() == 1 ? " occurrence" : " occurrences") + " in " + files
            + (files == 1 ? " file" : " files") + (listed.size() != shown.size() ? " · " + listed.size() + " shown" : ""));
        rebuilding = true;
        try {
            list.getItems().setAll(OccurrenceRow.of(listed));
            selected.flatMap(this::rowOf).ifPresent(i -> list.getSelectionModel().select(i));
        } finally {
            rebuilding = false;
        }
        if (list.getSelectionModel().getSelectedItem() == null) setFocus(Optional.empty());
        Platform.runLater(this::updateStickyHeader);
    }

    /** The row of the occurrence at the same place as {@code o} (after a re-parse, a new object). */
    private Optional<Integer> rowOf(TagOccurrence o) {
        List<OccurrenceRow> rows = list.getItems();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) instanceof OccurrenceRow.Occurrence r && r.occurrence().text().equals(o.text())
                && r.occurrence().tag().getTextCoordinate().startIndex() == o.tag().getTextCoordinate().startIndex()) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private void showPlaceholder() {
        list.setPlaceholder(new Label(index.size() == 0 ? "" : "Select a tag in the tree"));
    }

    private void contextChanged() {
        saveSettings(settings().withContext(beforeSpinner.getValue(), afterSpinner.getValue()));
        list.refresh();
    }

    /** The file of the topmost visible row, above the list, while the list is scrolled. */
    private void updateStickyHeader() {
        VirtualFlow<?> flow = (VirtualFlow<?>) list.lookup(".virtual-flow");
        IndexedCell<?> first = flow == null ? null : flow.getFirstVisibleCell();
        int index = first == null ? -1 : first.getIndex();
        String text = null;
        if (index > 0 && index < list.getItems().size()) {
            for (int i = index; i >= 0; i--) {
                if (list.getItems().get(i) instanceof OccurrenceRow.FileHeader h) {
                    // the header itself is visible: no need to repeat it
                    text = i == index ? null : h.text().displayName() + " — " + h.count()
                        + (h.count() == 1 ? " occurrence" : " occurrences");
                    break;
                }
            }
        }
        stickyHeader.setText(text == null ? "" : text);
        stickyHeader.setVisible(text != null);
        stickyHeader.setManaged(text != null);
    }

    /** Shows an occurrence in File Mode; it is selected first, so that Back returns to it. */
    private void edit(TagOccurrence o) {
        rowOf(o).ifPresent(i -> list.getSelectionModel().select(i.intValue()));
        workbench.navigator().showInEditor(Focus.of(o.tag()));
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    /** Selects the node and the occurrence of a tag. */
    @Override
    public boolean reveal(Focus focus) {
        if (focus.object().isEmpty() || !(focus.object().get() instanceof Tag)) return false;
        Optional<TagOccurrence> occurrence = index.all().stream()
            .filter(o -> o.text().equals(focus.text()) && o.tag().getTextCoordinate().startIndex() == focus.start())
            .findFirst();
        if (occurrence.isEmpty()) return false;
        if (!filterField.getText().isBlank()) filterField.clear();
        selectedPath = occurrence.get().path();
        if (!includeSubTags.isSelected() && appliesTo.getValue() != null && appliesTo.getValue().type() != null) {
            appliesTo.setValue(null);
        }
        for (int i = 1; i < selectedPath.size(); i++) {
            List<String> ancestor = selectedPath.subList(0, i);
            findItem(tree.getRoot(), ancestor).ifPresent(item -> item.setExpanded(true));
        }
        rebuildTree();
        showOccurrences();
        rowOf(occurrence.get()).ifPresent(i -> {
            list.getSelectionModel().select(i);
            list.scrollTo(Math.max(0, i - 1));
        });
        return true;
    }

    // ------------------------------------------------------------------
    // Rename and move
    // ------------------------------------------------------------------

    private Window window() {
        return root.getScene() == null ? workbench.window() : root.getScene().getWindow();
    }

    private void rename(TagNode node) {
        TagDialogs.rename(window(), node.path(), name -> TagPathEdit.rename(index.all(), node.path(), name),
                workbench.edits()::checkFields)
            .ifPresent(change -> apply(change, "Renamed"));
    }

    private void moveTo(TagNode node) {
        TagDialogs.chooseParent(window(), fullRoot, node, parent -> refusal(node.path(), parent))
            .ifPresent(parent -> move(node, parent));
    }

    /** Confirms and applies a move (from Move to… or a drop). */
    private void move(TagNode node, List<String> parent) {
        switch (TagPathEdit.move(index.all(), node.path(), parent)) {
            case TagPathEdit.Refused r -> workbench.status().warn(r.reason());
            case TagPathEdit.Change change -> {
                List<String> obstacles = workbench.edits().checkFields(change.changes());
                if (TagDialogs.confirmMove(window(), change, obstacles)) {
                    apply(change, "Moved");
                }
            }
        }
    }

    private Optional<String> refusal(List<String> path, List<String> parent) {
        return TagPathEdit.move(index.all(), path, parent) instanceof TagPathEdit.Refused r
            ? Optional.of(r.reason()) : Optional.empty();
    }

    /** Applies a planned change as one batch edit; the node is selected again once the texts are parsed. */
    private void apply(TagPathEdit.Change change, String verb) {
        ViewEditService.BatchResult result = workbench.edits().setFields(change.changes(), change.description());
        if (!result.applied()) {
            ErrorReporter.report(window(), "Nothing was changed", String.join("\n", result.problems()));
            return;
        }
        pendingPath = change.to();
        workbench.status().info(verb + " " + result.changes() + (result.changes() == 1 ? " tag" : " tags") + " in "
            + result.files() + (result.files() == 1 ? " file" : " files") + " (unsaved)", "Save all", workbench::saveAll);
    }

    // ------------------------------------------------------------------
    // Tree actions (context menu, drag and drop)
    // ------------------------------------------------------------------

    private final TagTreeCell.Actions treeActions = new TagTreeCell.Actions() {
        @Override
        public void rename(TagNode node) {
            TagBrowserMode.this.rename(node);
        }

        @Override
        public void moveTo(TagNode node) {
            TagBrowserMode.this.moveTo(node);
        }

        @Override
        public void expandAll(TagNode node, boolean expanded) {
            findItem(tree.getRoot(), node.path()).ifPresent(item -> setExpanded(item, expanded));
        }

        @Override
        public void copyKeyword(TagNode node) {
            TagBrowserMode.this.copyKeyword(node);
        }

        @Override
        public void dragging(TagNode node) {
            dragged = node;
            topLevelDrop.setVisible(node != null);
            topLevelDrop.setManaged(node != null);
        }

        @Override
        public Optional<TagNode> dragged() {
            return Optional.ofNullable(dragged);
        }

        @Override
        public Optional<String> refusal(List<String> parent) {
            return dragged == null ? Optional.of("") : TagBrowserMode.this.refusal(dragged.path(), parent);
        }

        @Override
        public void drop(List<String> parent) {
            TagNode node = dragged;
            dragging(null);
            if (node != null) move(node, parent);
        }
    };

    private void copyKeyword(TagNode node) {
        copy(node.keyword());
    }

    private static void copy(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void exportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export occurrences");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        chooser.setInitialFileName((selectedPath.isEmpty() ? "tags" : String.join("-", selectedPath)) + ".csv");
        File file = chooser.showSaveDialog(window());
        if (file == null) return;
        try {
            Files.writeString(file.toPath(), OccurrenceExport.csv(listed(), corpus()), StandardCharsets.UTF_8);
            workbench.status().info("Exported " + listed().size() + " occurrences to " + file.getName());
        } catch (IOException e) {
            ErrorReporter.report(window(), "Cannot export the occurrences", e);
        }
    }
}
