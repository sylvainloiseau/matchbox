# Plan: modes and window services

Status: implemented (not committed). Decisions taken with the user in §12.1; remaining questions
in §12.2 were implemented with the recommendation; differences between the plan and the code in §13.

## 0. Goal

Matchbox has one mode, File Mode, and the main window is written as if it were the only one:
- `MainController` calls `FileModeController` for almost every menu item;
- `FileModeController` owns services that every way of showing the corpus needs: the `ViewContext`
  given to views, the view edits, the media players and the status line;
- undo is per document, bound to the file displayed in the editor.

This plan separates three things:

| Concept | What it is | Lives |
|---|---|---|
| **Window** (workbench) | Menu bar, toolbar and status bar, plus the services shared by all modes: project, edits, undo journal, media, status, navigation | `MainController` + `gui.workbench` |
| **Mode** | One way to navigate, browse and edit the corpus objects in the main area (File Mode now; Corpus Mode, Tag Mode… later) | `gui.mode` |
| **Plugin** | A mapping from a notation to corpus objects; contributes no mode | `grammars/*` (see `matchbox-grammar-api/README.md`) |

After the refactoring, adding a mode means writing one class (and optionally its FXML) and adding one line to a list. No change to `MainController` or to the menus is needed, and no service is copied.

Out of scope: Corpus Mode and Tag Mode themselves (separate plans). This plan gives them the
contract they will implement and the services they will use.

---

## 1. Principles

1. **The text is the only source of truth.** A mode never changes a corpus object or writes a
   file. It asks for text edits through the `ViewContext` and the edit service. The document is
   changed and re-parsed, and every mode sees the new objects.
2. **The window's menus are mode-independent; each mode has one menu of its own.**
   - The window's menus (File, Edit, View, Assistant, Export) mean the same thing in every mode.
     They act on the project, on the project-wide undo journal, or on the **focus of the active
     mode** (§3.2). No mode adds, removes or rebinds items in them.
   - Functions specific to a mode go in **the mode's menu** (e.g. "File Mode"). This menu is in
     the menu bar while the mode is active (§4.4). Search, the editor's assistant actions and
     Insert are in the File Mode menu.
3. **Undo is project-wide.** Every change of any text is one entry of a single journal (§3.8).
   Undo reverts the last change made anywhere, whatever mode or file it was made in. The same
   journal answers the Edit menu, Ctrl+Z in the editor, and Ctrl+Z in the tables and views.
4. **Each mode has its own selection.** Switching modes does not move the other modes' selections.
   The window only reads the active mode's focus to target commands (Save current file, Close
   file, Play…). Navigation (show in editor, Back/Forward) changes a mode's selection on purpose.
5. **Window services exist once per window** and are shared by all modes:
   - project session, edits, journal, view context;
   - media players, status line, navigation;
   - object view registry, settings, assistant.
6. **Modes are created lazily and kept alive.**
   - A mode is built when first shown and stays in memory when the user switches away. Switching
     back is instant and keeps its state (scroll, selection, tabs).
   - There is no `shutdown` on modes. A mode owns no thread and no closable resource: anything to
     close is a window service, closed by the window.
7. **Modes work on capabilities, not on plugins.**
   - A mode reads the corpus through `CorpusView`, `Text` (`objectsOf(capability)`, `metadataOf`,
     `enclosingSections`, `targetOf`/`tagsOf`) and the capability interfaces.
   - It shows objects with `ObjectViewRegistry`, so plugin views appear in every mode.
8. **"Show in editor" means File Mode.** File Mode is always present and owns the text editor; any
   request to show a span in the text switches to it.

---

## 2. What is coupled today

| Concern | Today | After |
|---|---|---|
| Current project | `MainController.project`, pushed with `fileModeController.setProject` | `ProjectSession.projectProperty()` (§3.1) |
| `project.setApplyExecutor(Platform::runLater)` | `FileModeController.setProject` | `ProjectSession`, once per project |
| Listening to the corpus across project changes | `EditorController` re-registers itself | `ProjectSession.corpusEvents()`, which survive project changes |
| `ViewContext` | anonymous class in `FileModeController` | `WorkbenchViewContext`, one per window (§3.3) |
| `ViewEditService` | created by `FileModeController`; depends on `EditorController` (`rememberEdit`, `selectAfterParse`) | window service; publishes `EditHints` (§3.4) |
| Undo | `TextHistory` per document; menu bound to the displayed file | `EditJournal` per project (§3.8) |
| `SharedMediaPlayers` | field of `FileModeController`, closed in its `setProject`/`shutdown` | window service, closed on project change and at exit (§3.5) |
| Status messages | `assistMessageLabel` inside `FileMode.fxml` | `StatusLine`, in the window's status bar (§3.6) |
| AI status link | `assistStatusLink` inside `FileMode.fxml` | status bar (§3.6) |
| Selected object and its successor after a re-parse | `EditorController`: `findSuccessor`, `lastEdit*`, `pendingSelect*` | `ObjectSuccessor` (pure) + `SelectionTracker` (one per mode), fed by `EditHints` (§3.2) |
| `HighlightStyles` | owned by `EditorController` | window service (its colours are settings, used by every mode) |
| `ObjectViewRegistry` | loaded by `FileModeController` | window service, loaded once |
| `HostServices` | pushed into `FileModeController` | window service |
| Toggle buttons | three hand-written buttons in `MainView.fxml`, only one handled | generated from the list of modes (§4.3) |
| Menu items | all in `MainView.fxml`, calling `fileModeController`/`editorController` | window menus → `WorkbenchCommands` (§5); editor functions → the File Mode menu (§6.1) |
| `CreateProjectDialog` | receives the `FileModeController`, which it does not use | parameter removed |

Note: in `MainView.fxml`, `tagModeToggleButton` still has `toggleGroup="$viewToggleGroup"`,
although the group is now called `modeToggleGroup`. This undefined reference makes FXML loading
fail. The generated toolbar (§4.3) removes these buttons anyway.

---

## 3. Window services (`fr.cnrs.lacito.matchbox.gui.workbench`)

`Workbench` is the facade passed to every mode. `MainController` creates it, and it is closed when
the window closes:

```java
public final class Workbench implements AutoCloseable {
    public ProjectSession session();
    public EditJournalView journal();       // undo/redo of the current project (§3.8)
    public ViewContext viewContext();
    public ViewEditService edits();
    public EditHints editHints();
    public SharedMediaPlayers mediaPlayers();
    public StatusLine status();
    public Navigator navigator();
    public ObjectViewRegistry viewRegistry();
    public HighlightStyles highlightStyles();
    public UserSettings settings();
    public AssistController assist();
    public Window window();                 // owner of dialogs
    public void showDocument(String uri);   // HostServices
    @Override public void close();          // edits, players, assist
}
```

### 3.1 `ProjectSession`: the current project

- `ReadOnlyObjectProperty<Project> projectProperty()`. Modes listen to it instead of receiving
  `setProject`; a change of project resets what depends on the old project.
- `setProject(Project)` is called by `MainController` on new, open and close project. In order, it:
  1. closes the media players;
  2. sets the apply executor (`Platform::runLater`);
  3. moves the corpus listeners to the new corpus;
  4. calls `assist.setProject`;
  5. sets the property.
- `corpusEvents()`: `addListener(CorpusListener)` / `removeListener`.
  - These listeners are re-attached to each new project's corpus and are always called on the
    JavaFX thread.
  - A project change sends them no per-text events: they reset on `projectProperty`.
- `corpus()`: the current project's `CorpusView`.

### 3.2 Focus: what the active mode's commands target

Each mode has **its own selection**. The window does not share it between modes. A mode exposes
its **focus**: the text, the place and possibly the object the user is working on *in that mode*.

```java
/** Where the user is in a mode: a text, an offset in it, and possibly the object selected there. */
public record Focus(TextId text, int offset, Optional<CorpusObject> object) {
    public static Focus of(CorpusObject o);           // its text and start offset
    public static Focus at(TextId text, int offset);  // a caret position
}
```

- `Mode.focusProperty()` (§4.1). Some examples:
  - File Mode: the displayed file and the caret, plus the selected object;
  - Tag Mode: the selected tag;
  - Corpus Mode: the selected object.
- `ModeHost.focusProperty()` follows the focus of the active mode and changes when the active
  mode changes. `WorkbenchCommands` and the status bar use it; nothing writes it.
- **Keeping a selection valid across re-parses**: after a re-parse, all objects are new instances.
  The logic that finds the replacing object moves out of `EditorController` into two classes:
  - `ObjectSuccessor` (pure class, unit-tested) finds, in the new `Text`, the object that replaces
    a previous one.
  - `SelectionTracker` (a helper, one instance per mode) holds a mode's selected object, replaces
    it after each re-parse of its text, and clears it when the text is removed or the project
    changes.
- `EditHints` (window service): the edit service records where a change was made
  (`lastEdit(TextId, offset)`). A text action records what to select after the next parse
  (`selectAfterParse(TextId, offset)`). The `SelectionTracker`s consult these hints, so the mode
  where the edit was made keeps the edited object selected. This replaces
  `EditorController.rememberEdit` and `selectAfterParse`, and removes the dependency of
  `ViewEditService` on the editor.

### 3.3 `WorkbenchViewContext`: the single `ViewContext`

One instance per window, given to every view in every mode. It moves out of `FileModeController`
unchanged, except:

| Method | Behaviour |
|---|---|
| `selectInEditor(span)` | `navigator.showInEditor(span)`: switches to File Mode, opens the text, selects the span |
| `select(object)` | the **active mode** reveals it if it can (`Mode.reveal`); otherwise File Mode shows it |
| `undo(object)`, `redo(object)` | the project-wide journal (§3.8); the object only tells which project. Javadoc updated: "the last change of the project" |
| `runTextAction(action)` | applies to the active mode's focused text |
| `corpus()`, `canEdit`, `setField`, `addField`, `removeField`, `applyEdits` | `ProjectSession` / `ViewEditService`, as today |
| `highlightColor`, `mediaPlayers`, `projectDirectory`, `showDocument`, `metadataSchema` | window services |

API changes in matchbox-grammar-fx:
- a new method `default EditOutcome runTextAction(TextId text, TextAction action)`. The text an
  action was computed for becomes explicit (the editor margin uses it), instead of "the displayed
  document". It is a default method, so it is binary compatible.
- javadoc of `selectInEditor`, `select`, `undo`, `redo` updated.

### 3.4 `ViewEditService`

It becomes a window service, created once, depending on `ProjectSession`, `EditHints` and
`UserSettings`, and no longer on `EditorController`. Its behaviour is unchanged:
- edits of any document, displayed or not;
- validation, then re-parse.

Each edit gives an `EditOrigin` with a **description** for the journal (§3.8), e.g. "Edit
Translation", "Add metadata 'date'", or the label of a text action.

### 3.5 Media players

There is one `SharedMediaPlayers` per window. `ProjectSession` closes it when the project changes,
and `Workbench.close()` closes it at exit. One segment plays at a time, whatever mode started it,
and its `playingProperty` drives the ▶/⏸ state in every mode.

### 3.6 `StatusLine` and the status bar

The status bar of `MainView.fxml` (today "Left status" and an unused `statusBarRightLabel`) becomes
the window's:
- **left**: messages from `StatusLine`, through `info(String)`, `warn(String)` and
  `info(String, String linkText, Runnable action)` (a clickable link, e.g. "Show").
  - These calls replace `assistMessageLabel::setText` everywhere: `columnsFor(..., status)`, text
    actions, ghost text, "No media segment at the caret", refused edits.
- **right**:
  - the file name and line of the active mode's focus;
  - then the AI status link (`assist.statusProperty()`; clicking it opens the AI settings), moved
    from `FileMode.fxml`.

The Suggestions toggle stays in File Mode: the suggestions panel belongs to the editor.

### 3.7 `Navigator`

- `activate(String modeId)`: shows a mode (§4).
- `showInEditor(TextCoordinate span)`: switches to File Mode, selects the span's file, then selects
  and scrolls to the span.
- `reveal(CorpusObject)`: asks the active mode to show the object; if it cannot, File Mode shows it.
  `ViewContext.select` uses it.
- **Back / Forward** (Alt+← / Alt+→; Edit or View menu items; buttons at the left of the toolbar):
  - **What is recorded:** a history of navigation steps, each a `(modeId, Focus)` pair. A step is
    recorded before and after `showInEditor`, a `reveal` that falls back to File Mode, a mode
    switch from the toolbar or the View menu, and a jump made by a view (`select`).
  - **Back:** activates the recorded mode and asks it to `reveal(focus)`.
  - **Stale entries:** an entry's object is replaced by its successor (`ObjectSuccessor`) when its
    text has been re-parsed. An entry whose text has left the project is dropped.
  - **Size:** the history is bounded (50 entries) and cleared on project change.
  - Ordinary selections inside a mode (moving the caret, clicking a row) are not recorded. Only
    jumps are recorded, as in IDEs.

### 3.8 `EditJournal`: project-wide undo (matchbox-app)

It replaces the per-document `TextHistory`.

- **Owner.** `Project` owns one `EditJournal` (`project.journal()`). The journal listens to every
  document of the project: it starts on documents added and stops on documents removed.
- **Entries.** An entry is `(TextDocument, edits, inverse edits, description, offset, time)`.
  - Consecutive typing in the same document merges into one entry, with the rule of today's
    `TextHistory.mergeable`.
  - A view edit, a text action, an assistant suggestion or a search-and-replace is one entry each.
  - `preventMerge()` is kept: for example, the editor calls it when the caret jumps.
- **Undo** reverts the last entry on its document (`EditOrigin.UNDO`) and moves it to the redo
  stack. **Redo** re-applies it. Any new change, in any document, clears the redo stack.
- **Consistency.** All changes to a document go through `TextDocument.applyEdits`, and the journal
  is undone in LIFO order. So when an entry is undone, every later change to its document has
  already been undone, and its inverse edits apply to the exact content they were computed
  against. Changes to *other* documents do not interfere.
- **Removed documents.** When a document leaves the project, its entries are dropped (they can no
  longer be undone). The other entries stay valid. A new project has a new journal.
- **Saving** does not clear the journal: undoing after a save makes the document dirty again.
- **Size.** `TextHistory` has no bound today. Since the journal covers the whole project, it gets
  one: 1,000 entries, the oldest dropped first.
- **Properties for the UI.** `undoAvailableProperty`, `redoAvailableProperty`,
  `undoDescriptionProperty` and `redoDescriptionProperty`, e.g. "Edit Translation (notes.txt)".
- **Descriptions.** `EditOrigin` gains an optional `description`. When it is missing, the journal
  uses the kind: "Typing", "Apply suggestion", "Replace", "Undo"…
- `Workbench.journal()` follows `ProjectSession`: an `EditJournalView` whose properties rebind to
  the current project's journal.

**In the GUI**
- The Edit menu labels show the description and the file name, e.g. "Undo Edit Translation
  (notes.txt)". These labels are bound to the journal and do not depend on the mode.
- **Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y**, in the editor, the tables and the views, all call the
  journal. RichTextFX's own undo manager stays disabled, as today.
- **Undo in a file not shown by the active mode**: in File Mode, that is a file that is not
  displayed. The status line says "Undone: Edit Translation in other.txt", with a **Show** link
  (`navigator.showInEditor` at the entry's offset). Undo never switches files by itself.
- `Project.history(TextId)` and `TextHistory` are removed. `ViewEditService.undo/redo(object)` and
  `EditorController.undo/redo` delegate to the journal.

---

## 4. The mode contract (`fr.cnrs.lacito.matchbox.gui.mode`)

### 4.1 Interfaces

```java
/** A mode the window can show: what the toolbar needs, without creating the mode. */
public record ModeDescriptor(
    String id,                 // stable: "file", "corpus", "tag"
    String title,              // toolbar button and View menu: "File"
    String menuTitle,          // the mode's menu (§4.4): "File Mode"
    String description,        // tooltip: "List the files of the project, edit them and check how they are parsed"
    KeyCombination shortcut,   // Shortcut+1, Shortcut+2…
    Function<Workbench, Mode> factory) {}

/** One way to navigate, browse and edit the corpus in the main area of the window. */
public interface Mode {

    /** The content of the main area; built once, kept while the window lives. */
    Node root();

    /** The mode is shown. It keeps its own selection from its previous activation. */
    default void activate() {}

    /** The mode is hidden. It stays in memory with its state; it should stop costly work. */
    default void deactivate() {}

    /** What the commands target while this mode is active (§3.2); empty if nothing is selected. */
    ReadOnlyObjectProperty<Optional<Focus>> focusProperty();

    /**
     * Shows this place or object in this mode and selects it (navigation: ViewContext.select,
     * Back/Forward); false if this mode cannot show it.
     */
    default boolean reveal(Focus focus) { return false; }

    /**
     * The menu of this mode's own functions, shown in the menu bar while the mode is active
     * (§4.4); built once, like root(). Empty for a mode without specific functions.
     */
    default Optional<Menu> menu() { return Optional.empty(); }
}
```

A mode is constructed with the `Workbench`, on the JavaFX thread, the first time it is shown.

### 4.2 `AbstractMode`: the recommended base class

It implements the rule "kept alive but cheap when hidden":

- it subscribes to `session.corpusEvents()` and `session.projectProperty()`;
- while **active**, it calls `onTextsChanged(Set<TextId>)` as changes arrive;
- while **inactive**, it only accumulates the changed text ids (or a "project changed" flag), and
  calls `onTextsChanged` / `onProjectChanged` once, on the next `activate`;
- it provides `isActive()`, a `SelectionTracker` and the focus property.

This keeps the pattern in one place. For example, a hidden Tag Mode does not rebuild its tag tree
while the user types in File Mode. File Mode does not need the class: its editor follows its
document directly.

### 4.3 `ModeHost`, toolbar and menu

- `Modes.builtIn()` is the ordered list of `ModeDescriptor`s: the one place to register a mode.
  Plugins do not contribute modes. **For now it contains File Mode only.** Corpus Mode and Tag Mode
  are added by their own plans, each with its descriptor (§7).
- `ModeHost` (owned by `MainController`):
  - creates a mode on its first activation;
  - calls `deactivate` on the previous mode and `activate` on the new one;
  - puts the mode's `root()` in `viewPane` with anchors set to 0;
  - exposes `activeModeProperty()` and `focusProperty()` (§3.2).
- Toolbar:
  - the buttons are generated from the descriptors in one `ToggleGroup`, with the description as
    tooltip;
  - clicking the selected button keeps it selected (the current null guard);
  - the Back/Forward buttons sit at the left.
- The hand-written buttons and toggle groups disappear from `MainView.fxml`.
- A new **View** menu lists the modes as radio items with their shortcuts, plus Back and Forward. It
  depends on the list of modes, not on the active mode.
- `ModeSwitcher` holds the switching logic (which instance, lifecycle order). It is a pure class,
  tested without JavaFX; `ModeHost` is the thin JavaFX glue around it.
- The window always opens in File Mode. Remembering the last mode will come with the second mode
  (§12.2 Q4).

### 4.4 The mode menu

- **Position.** The menu bar shows the window's menus and, between View and Assistant, **the
  active mode's menu**:

  ```
  File   Edit   View   File Mode   Assistant   Export
  ```

- **Switching.** `ModeHost` replaces the menu when the active mode changes. It removes the previous
  mode's menu and inserts the new one at the same index. If the new mode has no menu, nothing is
  shown at that place.
- **Hidden rather than disabled.** A menu with a dozen disabled items would be noise, and a hidden
  mode's state (a caret, a selection) is not something the user can act on.
- **Accelerators.** A mode's accelerators work only while its menu is in the menu bar, so two modes
  can use the same shortcut for different functions. An accelerator must not collide with the
  window menus (checked by a test, §9).
- **What goes in it.** Functions that need the mode's UI state: the editor's caret and selection,
  the tables, the suggestions panel… A function that makes sense in every mode belongs to a window
  menu instead.
- **Building.** The mode builds the menu in Java, or in its FXML as a `<fx:define>`d `Menu` that it
  returns. Its handlers are methods of the mode.
- **Name clash.** The "File Mode" menu sits next to the window's "File" menu, which is about the
  project and its files. Their titles differ enough; the View menu and the toolbar use the mode
  titles.

---

## 5. Window menus (`WorkbenchCommands`)

The window's menus are handled by `WorkbenchCommands`: `MainController`'s handlers delegate to it.
Each command acts on one of three things, the same in every mode:
- the project;
- the journal;
- the **active mode's focus** (`ModeHost.focusProperty()`), written "focused text" below.

| Menu | Item | Behaviour | Enabled when |
|---|---|---|---|
| File | Project items, Settings, Quit | unchanged | — |
| File | Add file / directory / recent | adds to the project | always |
| File | Save current file | saves the focused text's document | it has unsaved changes |
| File | Save all files | `project.saveAll()` | any document is dirty |
| File | Close file | removes the focused text's file from the project, after confirmation | a text is focused |
| File | Close all files | removes all files, after confirmation (implements today's TODO) | the project has files |
| Edit | **Undo / Redo** | the project-wide journal (§3.8), labelled with the description and file | the journal allows it |
| Edit | Play media segment (⇧⌘Space) | the segment at the focus (`SegmentAtCaret.find(text, offset)`, or the focused object if it is a `MediaSegment`) | always (message if none) |
| View | modes, Back / Forward | `ModeHost`, `Navigator` history (§3.7) | — |
| Assistant | AI assistant settings…, Refresh project examples | unchanged (application and project level) | — |
| Export | Export dictionary | unchanged (project level) | — |

The items that need the editor move to the **File Mode menu** (§6.1):
- Search (§12.1);
- the Assistant items that act at the caret: Suggest completion, Inline suggestions, Explain
  error, Review block/selection/file, Suggestions panel;
- the Insert menu, which becomes a submenu.

The window then needs no typed access to File Mode, and no command switches mode by itself.

**Bindings.**
- When the active mode's focus changes, `WorkbenchCommands` rebinds Save current file and Close
  file to the focused document.
- Undo/Redo are bound once, to `Workbench.journal()`.
- `WorkbenchCommands` owns the bindings of the window menus, never a mode (unlike
  `switchToFileMode` today). A mode binds only the items of its own menu.

---

## 6. File Mode after the refactoring

`FileModeController` becomes `FileMode implements Mode` (§12.2 Q8), constructed with the
`Workbench`.

**It keeps:**
- the file list and the editor (`EditorController`: mirror of the document, highlighting, margin
  actions);
- the objects tabs and the detail pane;
- the errors tab and its plugin filter;
- the suggestions panel and the ghost text;
- the Parse/Save/Close buttons.

**It gives up to the window:**
- `ViewContext`, `ViewEditService`, `SharedMediaPlayers`, `ObjectViewRegistry`, `HostServices`;
- the status label and the AI status link;
- `setProject` (it listens to the session instead);
- `shutdown` (the window closes the services).

**It implements:**
- `focusProperty()`: the displayed file, the caret, and the selected object (from its
  `SelectionTracker`);
- `reveal(focus)`: always true; it selects the file, then the span or object;
- `menu()`: the File Mode menu (§6.1).

**`EditorController`:**
- keeps a `SelectionTracker` instead of `selectedObject`, `findSuccessor`, `lastEdit*` and
  `pendingSelect*`;
- no longer owns `HighlightStyles`;
- uses the window's `StatusLine`;
- sends undo/redo to the journal.

### 6.1 The File Mode menu

All the items act on the displayed file, at the caret or on the selection of the editor. They are
disabled when no file is displayed.

```
File Mode
├── Search…                               (find/replace in the displayed file; SearchDialog)
├── Parse file                            (today's Parse button)
├── ─────────
├── Suggest completion          Ctrl+Space
├── Inline suggestions                    (check item, setting)
├── Explain error at cursor
├── Review current block
├── Review selection                      (disabled without a selection)
├── Review whole file
├── Suggestions panel                     (check item, setting)
├── ─────────
└── Insert ▸
    ├── Insert intermorphemic gloss
    └── Insert lexical information
```

- **Search** keeps its current behaviour: `SearchDialog` on the code area. The replacements are one
  journal entry, described as "Replace".
- **Suggest completion**, the F8 navigation in suggestions and **Alt+Enter** stay handled by the
  editor's key filter, as today. The menu shows the shortcuts for discovery.
- **The Insert items** are today placeholders without handlers. They move as they are; implementing
  them is out of scope.
- **Shortcuts and context menus.** The editor's context menu (suggest, explain, review, play) and
  the error table's context menu (Explain, Show in suggestions panel) stay. They duplicate items of
  the mode menu where it is convenient.
- **Building the menu.** It is defined in `FileMode.fxml` (`<fx:define>`), and its handlers are
  methods of `FileMode`. `MainView.fxml` loses the Insert menu and these Assistant items.

---

## 7. Future modes

`Modes.builtIn()` lists File Mode only. The toolbar shows the File button and the View menu shows
File and Back/Forward. Each new mode adds its descriptor when it is implemented. The descriptions
already written in `MainView.fxml` become their tooltips:

| id | Title | Description |
|---|---|---|
| `file` | File | List the files in the project, edit/add corpus objects and check that they are correctly parsed |
| `corpus` | Corpus | Browse, search and edit the annotated texts in the corpus |
| `tag` | Tags | Browse, search, edit and organise all the tags in the corpus |

Tag Mode will list every `Tag` whatever plugin created it (`Text.objectsOf(Tag.class)`,
`targetOf`). It does not depend on the tag plugin or its `#"…"` notation. "Rename a tag
everywhere" will be one journal entry per document, or a grouped entry (§12.2 Q9).

---

## 8. Implementation steps

After each step, the code compiles, the behaviour is unchanged unless the step says otherwise, and
`mvn clean install` passes.

1. **Journal** (matchbox-app):
   - `EditJournal` with merging, descriptions, document removal and bound;
   - `EditOrigin.description`;
   - `Project.journal()`;
   - `TextHistory` and `Project.history` removed;
   - `EditorController`, `ViewEditService` and the Edit menu use the journal; Ctrl+Z everywhere;
   - tests.
2. **Workbench services**:
   - `Workbench`, `ProjectSession` (apply executor, corpus events);
   - `StatusLine` in the status bar (left: messages with link; right: focus and AI link);
   - `SharedMediaPlayers`, `ObjectViewRegistry`, `HighlightStyles` and `HostServices` move to the
     window;
   - `FileModeController` receives the `Workbench`;
   - `CreateProjectDialog` loses its unused parameter.
3. **Selection tracking**:
   - `ObjectSuccessor` (with tests), `SelectionTracker` and `EditHints`;
   - `ViewEditService` depends on `EditHints`, not on `EditorController`;
   - `EditorController` uses a `SelectionTracker`.
4. **ViewContext**:
   - `WorkbenchViewContext` (window);
   - `runTextAction(TextId, TextAction)` in matchbox-grammar-fx;
   - `Navigator.showInEditor` and `reveal`.
5. **Mode contract**:
   - `Mode`, `ModeDescriptor`, `Modes`, `ModeSwitcher` (with tests), `ModeHost`, `AbstractMode`;
   - `FileMode` implements `Mode`;
   - generated toolbar and View menu; the hand-written buttons are removed from `MainView.fxml`;
   - the mode menu slot in the menu bar (§4.4).
6. **Commands**:
   - `WorkbenchCommands`, with targets and bindings following the active mode's focus;
   - the File Mode menu (§6.1): Search, the editor's assistant items and Insert move out of
     `MainView.fxml` into `FileMode.fxml`;
   - Close all files implemented;
   - `MainController` reduced to the shell (menus, toolbar, status bar) and handlers delegating to
     `WorkbenchCommands`.
7. **Back/Forward**: navigation history in `Navigator` (with tests), menu items, toolbar buttons
   and shortcuts.
8. **Documentation**:
   - CLAUDE.md: §3 matchbox-app (journal), §3 matchbox-gui (workbench, modes), §12 key classes;
   - README (undo is project-wide; Back/Forward);
   - rewrite of the `create-mode` skill on the new contract.

---

## 9. Tests

Pure classes, without the JavaFX toolkit:

- `EditJournal` (matchbox-app):
  - undo/redo across two documents in chronological order;
  - typing merge, and no merge across documents;
  - redo cleared by a new change in another document;
  - the entries of a removed document are dropped and the others still undo correctly;
  - undo after save makes the document dirty;
  - bound;
  - descriptions.
- `ObjectSuccessor`: successor after an edit inside, before or after the object; deleted object;
  object of the same kind and label moved; pending selection.
- `SelectionTracker` with a `Corpus` (uses `javafx.base` properties only): it follows a re-parse,
  uses the edit hints, and clears when the text is removed or the project changes.
- `ModeSwitcher`:
  - lazy creation, one instance per mode;
  - `deactivate` before `activate`;
  - reselecting the active mode is a no-op.
- `AbstractMode` accumulation: changes received while inactive are delivered once, merged, on
  activation; a project change supersedes them.
- Navigation history:
  - back/forward order, and forward cleared by a new jump;
  - entries follow re-parses; dropped texts are skipped; the history is bounded.
- `WorkbenchCommands` targets: which document Save and Close use for a given focus.
- Mode menus (`ModeSwitcher` with fake modes): the active mode's menu replaces the previous one
  at the same index; a mode without a menu leaves the slot empty; no accelerator of a mode menu
  equals one of the window menus (checked for every built-in mode).

Manual checklist (`mvn -pl matchbox-gui javafx:run`):
- edit an IGT in a detail view, type in another file, then undo twice from the menu: the labels
  name the right files;
- undo a change of a file that is not displayed: check the status message and the Show link;
- "show in editor" from a view, then Back;
- play a segment;
- open and close a project;
- check that status messages appear in the status bar;
- the File Mode menu: Search, completion, review and explain work as from the old menus; the
  items are disabled when no file is displayed.

---

## 10. Files

New:
- `matchbox-app` `model/`: `EditJournal` (replaces `TextHistory`).
- `matchbox-gui` `gui/workbench/`:
  - `Workbench`, `ProjectSession`;
  - `Focus`, `SelectionTracker`, `ObjectSuccessor`, `EditHints`;
  - `WorkbenchViewContext`, `StatusLine`, `Navigator`, `NavigationHistory`;
  - `WorkbenchCommands`, `EditJournalView`.
- `matchbox-gui` `gui/mode/`:
  - `Mode`, `ModeDescriptor`, `Modes`;
  - `ModeHost`, `ModeSwitcher`, `AbstractMode`;
  - `FileMode` (renamed from `FileModeController`).

Changed:
- `Project`, `EditOrigin`;
- `MainController`, `MainView.fxml` (Insert menu and editor items of the Assistant menu moved
  out), `FileMode.fxml` (status widgets removed, File Mode menu added);
- `EditorController`, `ViewEditService`, `GhostTextController` (status), `SearchDialog` (journal
  description), `CreateProjectDialog`;
- `ViewContext` (matchbox-grammar-fx: one default method, javadoc).

---

## 11. Rules for future modes (basis of the `create-mode` skill)

- **Registering**: add a `ModeDescriptor` to `Modes.builtIn()`; the button, the View menu item and
  the shortcut follow.
- **Structure**:
  - extend `AbstractMode`;
  - FXML is optional; load it with `loader.setController(this)`; it goes in
    `resources/.../gui/mode/`;
  - mode-specific classes go in `gui.mode.<modeName>`; components shared by several modes go in
    `gui.mode.common`.
- **Reading**: use `session().corpus()` and the capabilities. Never test a plugin id or a kind to
  decide what an object is.
- **Showing and editing**: show objects with `viewRegistry()` and the `ViewContext`; edit only
  through the `ViewContext`; give edits a description for the journal.
- **Selection**: keep your own selection with a `SelectionTracker` and expose it as
  `focusProperty()`. Implement `reveal(focus)` if the mode can show arbitrary objects.
- **Menus and keys**:
  - put the mode's own functions in its menu (`menu()`, titled by `menuTitle`), with accelerators
    that do not collide with the window menus;
  - add no item to the window menus and rebind none; a command that makes sense in every mode goes
    in `WorkbenchCommands`;
  - Ctrl+Z goes to the journal.
- **Resources**: own no thread or closable resource. Messages go to `status()`, media to
  `mediaPlayers()`.

---

## 12. Decisions and questions

### 12.1 Decided with the user

- **Plugins contribute no mode.** A plugin maps a notation to corpus objects; several plugins may
  offer different notations for the same capability. This is documented in `CLAUDE.md`,
  `matchbox-grammar-api/README.md` and `README.md`.
- **Mode interface and window-level services**, with no `shutdown`: modes stay alive in the
  background.
- **The window menus are mode-independent; each mode has its own menu** ("File Mode"), shown in
  the menu bar while the mode is active.
- **Search stays an editor command**, in the File Mode menu, with the editor's assistant items and
  Insert. A corpus-wide search will be planned separately.
- **"Show in editor" switches to File Mode.**
- `ViewContext`, `SharedMediaPlayers` and the status line are window services used by all modes.
- **Undo: a project-wide journal**, also for Ctrl+Z in the editor, the tables and the views. An
  undone change in a file that is not shown is reported in the status line, with a Show link.
- **Per-mode selection**: the window only reads the active mode's focus to target commands.
- **Corpus and Tag modes are hidden** until implemented.
- **Back/Forward navigation**, in this refactoring, as the last code step.

### 12.2 Remaining questions (the plan assumes the recommendation)

**Q4. Startup mode.** *Recommendation*: always File Mode until a second mode exists. Then add a
`lastMode` setting.

**Q7. Button labels.** *Recommendation*: a short title on the button ("File") and the long
description as tooltip, so that the toolbar stays usable on small screens.

**Q8. Naming.** *Recommendation*: `Workbench` (package `gui.workbench`) for the window layer, and
`FileMode implements Mode` instead of `FileModeController`.

**Q9. Grouped journal entries.** A multi-file action, such as a future "rename tag everywhere",
could be one journal entry spanning several documents, undone in one step. *Recommendation*:
design `EditJournal` entries as a list of `(document, edits)` from the start, with one document
for now. The future action then needs no change to the journal.

Saving with multi-file changes. As for every edit today, a multi-file action or its undo only
changes the documents in memory. They become unsaved, and they are written by Save current file
or Save all files. On top of that:
- **Report changes to files that are not shown.** After a change or an undo that touches several
  files, the status line says so, e.g. "Renamed tag in 5 files (unsaved)" or "Undone: Rename tag
  (5 files, unsaved)". It offers a **Save all** link (`StatusLine.info(text, linkText, action)`,
  §3.6). A change to files the user cannot see is never silent.
- **No automatic save.** The notes are plain-text files that the user may also edit elsewhere, keep
  under git or sync. Writing them behind the user's back would be surprising. Keeping changes in
  memory costs nothing, since the journal works across saves (§3.8). Closing a file, closing the
  project or quitting still asks about unsaved files (`UnsavedChangesDialog`).

---

## 13. Differences between the plan and the code

- **Back/Forward shortcuts**: Shortcut+Alt+← / → instead of Alt+← / →, which moves by word in text
  fields on macOS.
- **The File Mode menu** is built in Java (`FileMode.buildMenu`), not in `FileMode.fxml`.
- `Mode.focusProperty()` returns an `ObservableValue<Optional<Focus>>`; `Focus` is a range
  (`start`, `end`), not only an offset, so that a selection in the editor can be revealed again.
- `ModeHost` checks the accelerators of a mode menu against the window's menus at run time (a
  warning in the log) and `ModeMenus.collisions` is unit-tested; File Mode's menu has no
  accelerators (its shortcuts are handled by the editor, as before).
- Revealing an object while File Mode is active (a view's `select`) is an ordinary selection, not
  a jump: it is not recorded in the history.
- Toolbar labels are short ("File"), with the description and shortcut as tooltip (Q7); no
  `lastMode` setting yet (Q4); `EditJournal.grouped(description, changes)` exists for multi-file
  actions (Q9).
- The Insert items keep no action, as before.
- `WorkbenchCommands` has no unit test (it needs the JavaFX modes); its targets are in the manual
  checklist (§9).
- When files are added and no file is displayed, File Mode shows the first one (as the old "Add
  file" did), whatever mode added them.
